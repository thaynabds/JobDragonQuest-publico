#!/usr/bin/env python3
"""Teste dos avisos contra a API em execução (padrão: http://127.0.0.1:8080), com o PostgreSQL de verdade.

Cria duas contas descartáveis e confere: preferências, mudança de etapa feita por e-mail gerando aviso uma única
vez, mudança manual sem aviso, tópico desligado, lembrete de entrevista com antecedência, horário de silêncio,
entrega, leitura, adiamento e isolamento entre contas. Nunca imprime tokens nem senhas. Sai com 1 na primeira falha.

Uso: python3 -I scripts/testar-avisos.py [URL_BASE]
"""
from __future__ import annotations

import datetime as dt
import json
import secrets
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")
if not BASE.startswith(("http://127.0.0.1", "http://localhost", "https://")):
    sys.exit("Use http://127.0.0.1, http://localhost ou https://.")
passed = 0


def call(method: str, path: str, token: str | None, body: dict | None = None):
    request = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode())
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def ok(condition: bool, name: str) -> None:
    global passed
    if name == "fila de entrega consultada":
        if not condition:
            print(f"FALHOU: {name}")
            sys.exit(1)
        return
    if not condition:
        print(f"FALHOU: {name}")
        sys.exit(1)
    passed += 1
    print(f"ok      {name}")


def account() -> str:
    status, body = call("POST", "/api/v1/auth/register", None, {
        "email": f"avisos-{secrets.token_hex(5)}@teste.dev",
        "password": secrets.token_urlsafe(18),
        "displayName": "Teste Avisos",
        "timeZone": "America/Recife",
    })
    ok(status == 201, "conta descartável criada")
    return body["accessToken"]


def application(token: str, state: str) -> str:
    status, body = call("POST", "/api/v1/applications", token, {
        "origin": "MANUAL", "company": f"Acme {secrets.token_hex(3)}", "title": "Dev Java",
        "url": f"https://acme.example/jobs/{secrets.token_hex(4)}", "state": state})
    ok(status == 201, f"candidatura criada em {state}")
    return body["id"]


def wait_for(token: str, kind: str, seconds: int = 45) -> list:
    """Na API real, as pendências são verificadas no máximo a cada 30 s por pessoa (refresh-interval)."""
    deadline = time.monotonic() + seconds
    while True:
        found = [n for n in due(token) if n["type"] == kind]
        if found or time.monotonic() > deadline:
            return found
        time.sleep(3)


def due(token: str) -> list:
    status, body = call("GET", "/api/v1/notifications/due", token)
    ok(status == 200, "fila de entrega consultada")
    return body


owner = account()
status, view = call("GET", "/api/v1/notifications/preferences", owner)
ok(status == 200 and len(view["preferences"]) == 27 and view["timeZone"] == "America/Recife", "preferências com 19 etapas e 8 tipos, no fuso da conta")

app = application(owner, "SUBMITTED")
call("POST", f"/api/v1/applications/{app}/transitions", owner, {"toState": "SCREENING", "source": "MANUAL", "evidence": "Eu mesma mudei"})
ok(due(owner) == [], "mudança feita pela própria pessoa não gera aviso")

status, _ = call("POST", f"/api/v1/applications/{app}/transitions", owner, {
    "toState": "INTERVIEW_SCHEDULED", "source": "EMAIL", "confidence": 0.95, "evidence": "Convite por e-mail"})
ok(status == 200, "mudança de etapa vinda de e-mail aplicada")
items = due(owner)
stage = [n for n in items if n["type"] == "STAGE_CHANGED"]
ok(len(stage) == 1 and stage[0]["state"] == "INTERVIEW_SCHEDULED" and stage[0]["system"], "aviso de etapa com notificação do sistema")
ok(len([n for n in due(owner) if n["type"] == "STAGE_CHANGED"]) == 1, "consultar de novo não repete o aviso")

status, _ = call("PUT", "/api/v1/notifications/preferences", owner, {
    "preferences": [{"topic": "STAGE:OFFER_RECEIVED", "enabled": False, "popup": True, "system": True, "sound": True}]})
ok(status == 200, "tópico de proposta desligado")
call("POST", f"/api/v1/applications/{app}/transitions", owner, {
    "toState": "OFFER_RECEIVED", "source": "EMAIL", "confidence": 0.95, "evidence": "Proposta"})
ok(not any(n.get("state") == "OFFER_RECEIVED" for n in due(owner)), "tópico desligado não avisa")

interview = application(owner, "SUBMITTED")
soon = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(minutes=40)).isoformat()
call("POST", f"/api/v1/applications/{interview}/transitions", owner, {
    "toState": "INTERVIEW_SCHEDULED", "source": "MANUAL", "evidence": "Marcada", "nextDueAt": soon})
reminders = wait_for(owner, "INTERVIEW_REMINDER")
ok(len(reminders) == 1 and reminders[0]["leadMinutes"] == 60, "lembrete de entrevista com 1 hora de antecedência")

ids = [n["id"] for n in due(owner)]
status, body = call("POST", "/api/v1/notifications/delivered", owner, {"ids": ids})
ok(status == 200 and body["count"] == len(ids) and due(owner) == [], "avisos marcados como entregues saem da fila")
status, body = call("POST", f"/api/v1/notifications/{ids[0]}/snooze", owner, {"minutes": 30})
ok(status == 200 and body["snoozedUntil"] and body["readAt"] is None, "adiar devolve como não lido depois do prazo")
status, body = call("POST", "/api/v1/notifications/read-all", owner)
ok(status == 200 and body["count"] >= 1, "marcar todos como lidos")
status, body = call("GET", "/api/v1/notifications", owner)
unread = [(n["type"], n["readAt"], n["snoozedUntil"]) for n in body["items"] if n["readAt"] is None]
if unread:
    print("não lidos:", unread)
ok(status == 200 and body["unreadCount"] == 0 and body["totalItems"] == 2, "histórico do sino com os 2 avisos, nenhum não lido")

status, _ = call("PUT", "/api/v1/notifications/preferences", owner, {
    "settings": {"quietEnabled": True, "quietStart": "00:00", "quietEnd": "23:59", "quietAllowHigh": False, "hideDetails": True}})
ok(status == 200, "horário de silêncio ligado")
status, test = call("POST", "/api/v1/notifications/test", owner)
ok(status == 200 and test["popup"] and not test["quiet"], "aviso de teste ignora o silêncio de propósito")
quiet_app = application(owner, "SUBMITTED")
call("POST", f"/api/v1/applications/{quiet_app}/transitions", owner, {
    "toState": "INTERVIEW_SCHEDULED", "source": "EMAIL", "confidence": 0.95, "evidence": "Convite"})
status, body = call("GET", "/api/v1/notifications?unreadOnly=true", owner)
quiet = [n for n in body["items"] if n["applicationId"] == quiet_app]
ok(len(quiet) == 1 and quiet[0]["quiet"] and not quiet[0]["popup"] and not quiet[0]["system"], "no silêncio o aviso fica só no sino")

status, body = call("PUT", "/api/v1/notifications/preferences", owner, {
    "preferences": [{"topic": "NEW_JOBS:00000000-0000-0000-0000-000000000000", "enabled": True, "popup": True, "system": True, "sound": True}]})
ok(status == 400 and body["code"] == "invalid_topic", "fonte que não é da conta é recusada")

intruder = account()
ok(call("POST", f"/api/v1/notifications/{test['id']}/read", intruder)[0] == 404, "outra conta não lê aviso alheio")
ok(call("GET", "/api/v1/notifications", intruder)[1]["totalItems"] == 0, "outra conta não vê avisos alheios")
ok(call("GET", "/api/v1/notifications", None)[0] == 401, "sem login não há avisos")

print(f"{passed} verificações de avisos passaram contra {BASE}.")
print("AVISOS_REAL_OK")
