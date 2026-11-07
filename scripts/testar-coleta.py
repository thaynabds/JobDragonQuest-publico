#!/usr/bin/env python3
"""Teste de coleta real do JobDragonQuest contra um ATS público (padrão: board "gitlab" no Greenhouse).

Cria um usuário descartável, um perfil e uma fonte Greenhouse, pede a coleta pela API e confere:
- que vagas reais foram gravadas e ligadas à fonte;
- que uma segunda coleta não duplica nada;
- que o histórico registra as duas execuções.
Não imprime tokens nem senhas. Exige rede até boards-api.greenhouse.io. Sai com código 1 na primeira falha.

Uso: scripts/testar-coleta.py [URL_BASE] [BOARD]
"""
from __future__ import annotations

import json
import secrets
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")
BOARD = sys.argv[2] if len(sys.argv) > 2 else "gitlab"
if not BASE.startswith(("http://127.0.0.1", "http://localhost", "https://")):
    sys.exit("Use http://127.0.0.1, http://localhost ou https://.")

token: str | None = None


def call(method: str, path: str, body: dict | None = None):
    request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def check(name: str, condition: bool, detail: object = "") -> None:
    if not condition:
        print(f"FALHOU  {name}  {detail}")
        sys.exit(1)
    print(f"ok      {name}")


status, body = call("POST", "/api/v1/auth/register", {
    "email": f"coleta-{secrets.token_hex(6)}@teste.dev", "password": secrets.token_urlsafe(18), "displayName": "Teste de Coleta"})
check("cadastro", status == 201, status)
token = body["accessToken"]

status, profile = call("POST", "/api/v1/profiles", {
    "name": "Engenharia remota", "seniority": "MID", "minMatchScore": 50, "workModes": ["REMOTE"],
    "desiredTitles": ["Engineer"], "skills": [{"name": "Go", "level": 3}, {"name": "Ruby", "level": 3},
                                              {"name": "Kubernetes", "level": 3}, {"name": "PostgreSQL", "level": 3}]})
check("perfil", status == 201, status)

status, created = call("POST", "/api/v1/sources/bulk", {
    "profileIds": [profile["id"]], "keywords": ["Engineer"], "frequencyMinutes": 1440,
    "sources": [{"name": "GitLab", "kind": "GREENHOUSE", "identifier": BOARD}]})
check("fonte Greenhouse cadastrada", status == 201 and len(created["created"]) == 1, (status, created))
source = created["created"][0]["id"]

status, first = call("POST", f"/api/v1/sources/{source}/run")
check("primeira coleta OK", status == 200 and first["status"] == "OK", (status, first))
check("vagas reais gravadas", first["created"] > 0, first)
print(f"        {first['message']}")

status, jobs = call("GET", f"/api/v1/jobs?sourceId={source}&size=100")
check("vagas ligadas à fonte", status == 200 and jobs["totalElements"] == first["created"]
      and all(job["watchedSourceId"] == source for job in jobs["content"]), (status, jobs.get("totalElements")))
check("URLs das vagas são http(s)", all(job["url"].startswith(("https://", "http://")) for job in jobs["content"]))

status, second = call("POST", f"/api/v1/sources/{source}/run")
check("segunda coleta não duplica", status == 200 and second["status"] == "OK" and second["created"] == 0, second)

status, runs = call("GET", f"/api/v1/sources/{source}/runs")
check("histórico com as duas coletas", status == 200 and len(runs) == 2, runs)

print("COLETA_REAL_OK")
