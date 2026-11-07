"""Testes do plugin do JobFly para o Congo, sem rede e sem discord.py."""

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
import urllib.error
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("jobfly_plugin", HERE / "jobfly.py")
plugin = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(plugin)

TOKEN = "jfp_" + "b" * 43
OWNER = "123456789012345678"


def write_config(folder: Path, body: str, mode: int = 0o600) -> Path:
    path = folder / "congo.env"
    path.write_text(body, encoding="utf-8")
    os.chmod(path, mode)
    return path


class FakeApi:
    def __init__(self, due: list, hide: bool = False):
        self.due = due
        self.hide = hide
        self.calls: list[tuple[str, str]] = []

    def request(self, method: str, path: str, body: object | None = None) -> object:
        self.calls.append((method, path))
        if path.endswith("/due"):
            return self.due
        if path.endswith("/preferences"):
            return {"settings": {"hideDetails": self.hide}}
        raise AssertionError(path)


class ConfigTest(unittest.TestCase):
    def test_reads_valid_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write_config(Path(tmp), f"JOBFLY_TOKEN={TOKEN}\nJOBFLY_DISCORD_USER_ID={OWNER}\nJOBFLY_POLL_SECONDS=5\n")
            config = plugin.load_config(path)
        self.assertEqual(config.api_url, "http://127.0.0.1:8080")
        self.assertEqual(config.owner_id, int(OWNER))
        self.assertEqual(config.poll_seconds, 30, "o intervalo mínimo é 30 s")

    def test_refuses_loose_permissions_and_does_not_leak_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write_config(Path(tmp), f"JOBFLY_TOKEN={TOKEN}\nJOBFLY_DISCORD_USER_ID={OWNER}\n", 0o644)
            with self.assertRaises(plugin.ConfigError) as ctx:
                plugin.load_config(path)
        self.assertIn("chmod 600", str(ctx.exception))
        self.assertNotIn(TOKEN, str(ctx.exception))

    def test_refuses_bad_token_owner_and_url(self):
        cases = [
            "JOBFLY_TOKEN=jfp_curto\nJOBFLY_DISCORD_USER_ID=" + OWNER,
            f"JOBFLY_TOKEN={TOKEN}\nJOBFLY_DISCORD_USER_ID=@dona",
            f"JOBFLY_TOKEN={TOKEN}\nJOBFLY_DISCORD_USER_ID={OWNER}\nJOBFLY_API_URL=http://evil.example.com",
            f"JOBFLY_TOKEN={TOKEN}\nJOBFLY_DISCORD_USER_ID={OWNER}\nJOBFLY_API_URL=http://user:pw@127.0.0.1:8080",
        ]
        for body in cases:
            with self.subTest(body=body), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(plugin.ConfigError) as ctx:
                    plugin.load_config(write_config(Path(tmp), body))
                self.assertNotIn(TOKEN, str(ctx.exception))
        self.assertEqual(plugin.validate_url("https://jobfly.example.com/"), "https://jobfly.example.com")
        with self.assertRaises(plugin.ConfigError):
            plugin.load_config(Path("/caminho/que/nao/existe.env"))


class MessagesTest(unittest.TestCase):
    def test_hide_details_and_clip(self):
        item = {"priority": "HIGH", "message": "Entrevista com Acme (Dev) em 07/11"}
        self.assertEqual(plugin.notification_text(item, False), "JobFly · Importante: Entrevista com Acme (Dev) em 07/11")
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
    def test_sends_only_system_items_once_without_marking_delivered(self):
        with tempfile.TemporaryDirectory() as tmp:
            sent = plugin.SentLog(Path(tmp) / "enviados.json")
            api = FakeApi([
                {"id": "a", "system": True, "priority": "HIGH", "message": "Proposta recebida"},
                {"id": "b", "system": False, "priority": "LOW", "message": "Sem atualização"},
            ])
            batch = plugin.pending_dms(api, sent)
            self.assertEqual(batch, [("a", "JobFly · Importante: Proposta recebida")])
            sent.add("a")
            self.assertEqual(plugin.pending_dms(api, plugin.SentLog(Path(tmp) / "enviados.json")), [])
            self.assertFalse(any(path.endswith("/delivered") for _, path in api.calls))
            self.assertEqual(os.stat(Path(tmp) / "enviados.json").st_mode & 0o777, 0o600)

    def test_respects_hide_details(self):
        with tempfile.TemporaryDirectory() as tmp:
            batch = plugin.pending_dms(FakeApi([{"id": "a", "system": True, "message": "Acme (Dev)"}], hide=True), plugin.SentLog(Path(tmp) / "s.json"))
        self.assertNotIn("Acme", batch[0][1])

    def test_sent_log_survives_corrupt_file_and_caps_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "s.json"
            path.write_text("{não é json", encoding="utf-8")
            log = plugin.SentLog(path)
            for index in range(plugin.MAX_REMEMBERED + 10):
                log.add(str(index))
            self.assertEqual(len(json.loads(path.read_text(encoding="utf-8"))), plugin.MAX_REMEMBERED)


class ApiTest(unittest.TestCase):
    def config(self):
        return plugin.Config("http://127.0.0.1:8080", TOKEN, int(OWNER), 60)

    def test_unauthorized_message_has_no_token(self):
        class Opener:
            def open(self, request, timeout):
                raise urllib.error.HTTPError(request.full_url, 401, "Unauthorized", {}, None)

        with self.assertRaises(plugin.ApiError) as ctx:
            plugin.JobFlyApi(self.config(), Opener()).request("GET", "/api/v1/dashboard/summary")
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

        api = plugin.JobFlyApi(self.config(), Opener())
        with self.assertRaises(ValueError):
            api.request("GET", "/actuator/env")
        with self.assertRaises(plugin.ApiError):
            api.request("GET", "/api/v1/dashboard/summary")


if __name__ == "__main__":
    unittest.main()
