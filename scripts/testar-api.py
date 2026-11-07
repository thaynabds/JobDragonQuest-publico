#!/usr/bin/env python3
"""Teste de fumaça do JobDragonQuest contra a API em execução (padrão: http://127.0.0.1:8080).

Percorre o fluxo real: cadastro, perfil, vaga, compatibilidade, candidatura externa, deduplicação, evidência,
sugestão de baixa confiança, Kanban, pendências e fontes vigiadas em lote para vários perfis. Cria um usuário descartável com e-mail aleatório e nunca imprime
tokens nem senhas. Sai com código 1 na primeira falha.

Uso: scripts/testar-api.py [URL_BASE]
"""
from __future__ import annotations

import http.cookiejar
import json
import secrets
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")
if not BASE.startswith(("http://127.0.0.1", "http://localhost", "https://")):
    sys.exit("Use http://127.0.0.1, http://localhost ou https://.")

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
token: str | None = None
passed = 0


def call(method: str, path: str, body: dict | None = None, headers: dict | None = None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with opener.open(request, timeout=30) as response:
            raw = response.read()
            return response.status, dict(response.headers), json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, dict(error.headers), json.loads(raw) if raw else None


def check(name: str, condition: bool, detail: object = "") -> None:
    global passed
    if not condition:
        print(f"FALHOU  {name}  {detail}")
        sys.exit(1)
    passed += 1
    print(f"ok      {name}")


status, _, body = call("GET", "/actuator/health")
check("health", status == 200 and body.get("status") == "UP", body)

email = f"fumaca-{secrets.token_hex(6)}@teste.dev"
password = secrets.token_urlsafe(18)
status, _, body = call("POST", "/api/v1/auth/register",
                       {"email": email, "password": password, "displayName": "Teste de Fumaça"})
check("cadastro", status == 201 and body.get("tokenType") == "Bearer", status)
token = body["accessToken"]
check("cookie de refresh HttpOnly", any(c.name == "jobdragonquest_rt" for c in jar))

status, _, body = call("GET", "/api/v1/me")
check("me", status == 200 and body["email"] == email, status)

status, _, profile = call("POST", "/api/v1/profiles", {
    "name": "Desenvolvedora Java", "seniority": "JUNIOR", "minMatchScore": 60,
    "desiredTitles": ["Desenvolvedora Java"], "locations": ["Recife"], "workModes": ["REMOTE", "HYBRID"],
    "skills": [{"name": "Java", "level": 4, "years": 1.5}, {"name": "Spring Boot", "level": 3, "years": 1}],
    "languages": [{"code": "pt", "level": "NATIVE"}, {"code": "en", "level": "B1"}]})
check("perfil criado", status == 201 and profile["version"] == 0, status)

status, _, updated = call("PUT", f"/api/v1/profiles/{profile['id']}", {**{k: profile[k] for k in (
    "name", "seniority", "desiredTitles", "locations", "workModes", "skills", "languages")},
    "minMatchScore": 65, "version": profile["version"]})
check("perfil atualizado com lock otimista", status == 200 and updated["version"] == 1, (status, updated))

job_body = {"source": "LINKEDIN", "url": "https://br.linkedin.com/jobs/view/dev-java-junior-at-acme-123456?trk=x",
            "company": "Acme Ltda", "title": "Desenvolvedor Java Júnior", "workMode": "REMOTE", "seniority": "JUNIOR",
            "requirements": [{"kind": "MANDATORY", "text": "Java", "skill": "Java", "minYears": 1},
                             {"kind": "MANDATORY", "text": "Experiência com Spring Boot"},
                             {"kind": "DESIRED", "text": "Kafka", "skill": "Kafka"}]}
status, _, job = call("POST", "/api/v1/jobs", job_body)
check("vaga criada", status == 201, status)
status, headers, _ = call("POST", "/api/v1/jobs", {**job_body, "url": "https://www.linkedin.com/jobs/view/123456/"})
check("vaga duplicada reconhecida", status == 200 and headers.get("X-Duplicate-Of") == job["id"], status)

status, _, matches = call("GET", f"/api/v1/jobs/{job['id']}/matches")
check("compatibilidade", status == 200 and matches[0]["score"] == 70 and matches[0]["recommendation"] == "APPLY",
      matches[0] if matches else status)

status, _, app = call("POST", "/api/v1/applications",
                      {"origin": "AUTOMATION", "jobId": job["id"], "profileId": profile["id"]},
                      {"Idempotency-Key": "fumaca-" + secrets.token_hex(6)})
check("candidatura ligada à vaga", status == 201 and app["matchScore"] == 70 and app["state"] == "JOB_FOUND", status)

status, _, external = call("POST", "/api/v1/applications", {
    "origin": "SOCIAL", "platform": "Instagram", "company": "Tech Recife", "title": "Estágio em Desenvolvimento",
    "url": "https://www.instagram.com/p/XYZ/?igshid=1"})
check("candidatura externa (Instagram)", status == 201 and external["state"] == "SUBMITTED", status)
status, headers, _ = call("POST", "/api/v1/applications",
                          {"origin": "MANUAL", "company": "TECH RECIFE", "title": "estágio em desenvolvimento"})
check("candidatura duplicada reconhecida", status == 200 and headers.get("X-Duplicate-Of") == external["id"], status)

path = f"/api/v1/applications/{external['id']}/transitions"
status, _, result = call("POST", path, {"toState": "CONFIRMED", "source": "EMAIL", "confidence": 0.95,
                                        "evidence": "Recebemos sua candidatura"})
check("evidência por e-mail aplicada", status == 200 and result["outcome"] == "APPLIED", status)
status, _, result = call("POST", path, {"toState": "INTERVIEW_SCHEDULED", "source": "AGENT", "confidence": 0.5,
                                        "evidence": "Menção vaga a uma conversa"})
check("baixa confiança vira sugestão", status == 202 and result["application"]["state"] == "CONFIRMED", status)
status, _, result = call("POST", path, {"toState": "SUBMITTED", "source": "EMAIL", "confidence": 1, "evidence": "x"})
check("fonte automática não volta etapa", status == 409, status)

status, _, board = call("GET", "/api/v1/applications/board")
check("kanban com 19 etapas", status == 200 and len(board) == 19, status)
status, _, summary = call("GET", "/api/v1/dashboard/summary")
check("visão geral", status == 200 and summary["applicationsTotal"] == 2 and summary["jobsFound"] == 1
      and summary["byState"]["CONFIRMED"] == 1, summary)
status, _, pending = call("GET", "/api/v1/dashboard/pending")
check("pendência da sugestão", status == 200 and any(p["type"] == "SUGGESTION_TO_REVIEW" for p in pending), pending)

# Fontes vigiadas: várias de uma vez, ligadas a dois perfis, com duplicata reconhecida e edição em lote.
status, _, second_profile = call("POST", "/api/v1/profiles", {"name": "Estágio", "seniority": "INTERN", "desiredTitles": ["Estágio"]})
check("segundo perfil", status == 201, status)
both = [profile["id"], second_profile["id"]]
status, _, batch = call("POST", "/api/v1/sources/bulk", {"profileIds": both, "frequencyMinutes": 720, "sources": [
    {"name": "Nubank", "kind": "GREENHOUSE", "identifier": "nubank"},
    {"name": "Vagas Java", "kind": "LINKEDIN", "url": "https://www.linkedin.com/jobs/search/?keywords=java"},
    {"name": "#VagasTI", "kind": "INSTAGRAM", "url": "https://www.instagram.com/explore/tags/vagasti/"}]})
check("três fontes em lote para dois perfis", status == 201 and len(batch["created"]) == 3
      and all(sorted(s["profileIds"]) == sorted(both) for s in batch["created"]), (status, batch))
status, _, again = call("POST", "/api/v1/sources/bulk", {"sources": [{"name": "Nubank", "kind": "GREENHOUSE", "identifier": "NUBANK"}]})
check("fonte duplicada reconhecida", status == 201 and len(again["created"]) == 0 and len(again["duplicates"]) == 1, again)
ids = [s["id"] for s in batch["created"]]
status, _, paused = call("POST", "/api/v1/sources/bulk-update", {"sourceIds": ids[:2], "enabled": False})
check("pausa em lote", status == 200 and all(not s["enabled"] for s in paused), paused)
status, _, pending = call("GET", "/api/v1/dashboard/pending")
check("fonte assistida a conferir nas pendências", any(p["type"] == "SOURCE_TO_CHECK" and p["sourceId"] == ids[2] for p in pending), pending)
status, _, run = call("POST", f"/api/v1/sources/{ids[2]}/check", {"jobsRegistered": 1, "note": "Uma vaga encontrada"})
check("fonte assistida conferida", status == 200 and run["status"] == "CHECKED", (status, run))

token = None
status, _, _ = call("GET", "/api/v1/profiles")
check("sem token: 401", status == 401, status)
status, _, body = call("POST", "/api/v1/auth/refresh", headers={"Origin": "http://localhost:5173"})
check("refresh pelo cookie", status == 200 and body.get("accessToken"), status)

print(f"\n{passed} verificações passaram contra {BASE}.")
