"""Testes do plugin do JobDragonQuest para o Congo, sem rede e sem discord.py."""

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
import urllib.error
from datetime import datetime, UTC
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("jobdragonquest_plugin", HERE / "jobdragonquest.py")
plugin = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(plugin)

TOKEN = "jdq_" + "b" * 43
OWNER = "123456789012345678"


def write_config(folder: Path, body: str, mode: int = 0o600) -> Path:
    path = folder / "congo.env"
    path.write_text(body, encoding="utf-8")
    os.chmod(path, mode)
    return path


class FakeApi:
    def __init__(self, items: list, hide: bool = False):
        self.items = items
        self.hide = hide
        self.calls: list[tuple[str, str]] = []

    def request(self, method: str, path: str, body: object | None = None) -> object:
        self.calls.append((method, path))
        if path.startswith("/api/v1/notifications?"):
            return {"items": list(reversed(self.items))}  # a API devolve do mais novo para o mais antigo
        if path.endswith("/preferences"):
            return {"settings": {"hideDetails": self.hide}}
        raise AssertionError(path)


START = datetime(2026, 11, 7, 5, 0, tzinfo=UTC)
LATER = "2026-11-07T05:10:00Z"


class ConfigTest(unittest.TestCase):
    def test_reads_valid_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write_config(Path(tmp), f"JOBDRAGONQUEST_TOKEN={TOKEN}\nJOBDRAGONQUEST_DISCORD_USER_ID={OWNER}\nJOBDRAGONQUEST_POLL_SECONDS=5\n")
            config = plugin.load_config(path)
        self.assertEqual(config.api_url, "http://127.0.0.1:8080")
        self.assertEqual(config.owner_id, int(OWNER))
        self.assertEqual(config.poll_seconds, 30, "o intervalo mínimo é 30 s")

    def test_refuses_loose_permissions_and_does_not_leak_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write_config(Path(tmp), f"JOBDRAGONQUEST_TOKEN={TOKEN}\nJOBDRAGONQUEST_DISCORD_USER_ID={OWNER}\n", 0o644)
            with self.assertRaises(plugin.ConfigError) as ctx:
                plugin.load_config(path)
        self.assertIn("chmod 600", str(ctx.exception))
        self.assertNotIn(TOKEN, str(ctx.exception))

    def test_refuses_bad_token_owner_and_url(self):
        cases = [
            "JOBDRAGONQUEST_TOKEN=jdq_curto\nJOBDRAGONQUEST_DISCORD_USER_ID=" + OWNER,
            f"JOBDRAGONQUEST_TOKEN={TOKEN}\nJOBDRAGONQUEST_DISCORD_USER_ID=@dona",
            f"JOBDRAGONQUEST_TOKEN={TOKEN}\nJOBDRAGONQUEST_DISCORD_USER_ID={OWNER}\nJOBDRAGONQUEST_API_URL=http://evil.example.com",
            f"JOBDRAGONQUEST_TOKEN={TOKEN}\nJOBDRAGONQUEST_DISCORD_USER_ID={OWNER}\nJOBDRAGONQUEST_API_URL=http://user:pw@127.0.0.1:8080",
        ]
        for body in cases:
            with self.subTest(body=body), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(plugin.ConfigError) as ctx:
                    plugin.load_config(write_config(Path(tmp), body))
                self.assertNotIn(TOKEN, str(ctx.exception))
        self.assertEqual(plugin.validate_url("https://jobdragonquest.example.com/"), "https://jobdragonquest.example.com")
        with self.assertRaises(plugin.ConfigError):
            plugin.load_config(Path("/caminho/que/nao/existe.env"))


class MessagesTest(unittest.TestCase):
    def test_hide_details_and_clip(self):
        item = {"priority": "HIGH", "message": "Entrevista com Acme (Dev) em 07/11"}
        self.assertEqual(plugin.notification_text(item, False), "JobDragonQuest · Importante: Entrevista com Acme (Dev) em 07/11")
        hidden = plugin.notification_text(item, True)
        self.assertNotIn("Acme", hidden)
        long = plugin.notification_text({"message": "x" * 5000}, False)
        self.assertLessEqual(len(long), plugin.MAX_MESSAGE)

    def test_summary_and_lists(self):
        text = plugin.summary_text({"jobsFound": 12, "offers": 1})
        self.assertIn("Vagas encontradas: 12", text)
        self.assertIn("Propostas: 1", text)
        self.assertIn("Nenhuma", plugin.list_text("Pendências", [], lambda p: "", "Nenhuma pendência."))
        self.assertIn("• A", plugin.list_text("Avisos", [{"message": "A"}], lambda n: n["message"], ""))


class DeliveryTest(unittest.TestCase):
    def log(self, tmp: str) -> plugin.SentLog:
        log = plugin.SentLog(Path(tmp) / "enviados.json")
        log.start(START)
        return log

    def test_first_run_only_marks_the_start(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = plugin.SentLog(Path(tmp) / "enviados.json")
            api = FakeApi([{"id": "velho", "system": True, "createdAt": "2026-11-01T10:00:00Z", "message": "antigo"}])
            self.assertEqual(plugin.pending_dms(api, log, START), [])
            self.assertEqual(api.calls, [], "na primeira vez não consulta nem manda nada")
            self.assertEqual(plugin.SentLog(Path(tmp) / "enviados.json").since, START)
            self.assertEqual(os.stat(Path(tmp) / "enviados.json").st_mode & 0o777, 0o600)

    def test_sends_system_items_even_after_the_panel_delivered_them(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = self.log(tmp)
            api = FakeApi([
                {"id": "a", "system": True, "priority": "HIGH", "createdAt": LATER, "deliveredAt": LATER, "message": "Proposta recebida"},
                {"id": "b", "system": False, "createdAt": LATER, "message": "Sem atualização"},
                {"id": "c", "system": True, "createdAt": LATER, "readAt": LATER, "message": "Já lido no painel"},
                {"id": "d", "system": True, "createdAt": LATER, "snoozedUntil": "2026-11-07T08:00:00Z", "message": "Adiado"},
                {"id": "e", "system": True, "createdAt": "2026-11-07T04:59:00Z", "message": "Antes do início"},
            ])
            now = datetime(2026, 11, 7, 5, 15, tzinfo=UTC)
            batch = plugin.pending_dms(api, log, now)
            self.assertEqual(batch, [("a", "JobDragonQuest · Importante: Proposta recebida")])
            log.add("a")
            self.assertEqual(plugin.pending_dms(api, plugin.SentLog(Path(tmp) / "enviados.json"), now), [])
            self.assertFalse(any("/delivered" in path for _, path in api.calls))

    def test_respects_hide_details(self):
        with tempfile.TemporaryDirectory() as tmp:
            api = FakeApi([{"id": "a", "system": True, "createdAt": LATER, "message": "Acme (Dev)"}], hide=True)
            batch = plugin.pending_dms(api, self.log(tmp), datetime(2026, 11, 7, 6, 0, tzinfo=UTC))
        self.assertNotIn("Acme", batch[0][1])

    def test_sent_log_survives_corrupt_or_old_file_and_caps_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "s.json"
            path.write_text("{não é json", encoding="utf-8")
            self.assertIsNone(plugin.SentLog(path).since)
            path.write_text(json.dumps(["x", "y"]), encoding="utf-8")
            old = plugin.SentLog(path)
            self.assertIn("x", old)
            old.start(START)
            for index in range(plugin.MAX_REMEMBERED + 10):
                old.add(str(index))
            self.assertEqual(len(json.loads(path.read_text(encoding="utf-8"))["ids"]), plugin.MAX_REMEMBERED)


class ApiTest(unittest.TestCase):
    def config(self):
        return plugin.Config("http://127.0.0.1:8080", TOKEN, int(OWNER), 60)

    def test_unauthorized_message_has_no_token(self):
        class Opener:
            def open(self, request, timeout):
                raise urllib.error.HTTPError(request.full_url, 401, "Unauthorized", {}, None)

        with self.assertRaises(plugin.ApiError) as ctx:
            plugin.JobDragonQuestApi(self.config(), Opener()).request("GET", "/api/v1/dashboard/summary")
        self.assertEqual(ctx.exception.status, 401)
        self.assertNotIn(TOKEN, str(ctx.exception))

    def test_rejects_paths_outside_the_api_and_oversized_responses(self):
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self, size):
                return b"x" * size

        class Opener:
            def open(self, request, timeout):
                return Response()

        api = plugin.JobDragonQuestApi(self.config(), Opener())
        with self.assertRaises(ValueError):
            api.request("GET", "/actuator/env")
        with self.assertRaises(plugin.ApiError):
            api.request("GET", "/api/v1/dashboard/summary")


if __name__ == "__main__":
    unittest.main()
