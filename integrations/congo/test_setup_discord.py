"""Confere o setup() com o discord.py de verdade, sem conectar ao Discord. Pulado se o discord.py não estiver instalado
(rode com o Python do ambiente do Congo: congo-grey-parrot-discord/.venv/bin/python -m unittest ...)."""

from __future__ import annotations

import asyncio
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path

try:
    import discord
    from discord import app_commands
except ImportError:  # pragma: no cover
    discord = None

HERE = Path(__file__).resolve().parent


@unittest.skipIf(discord is None, "discord.py não instalado neste Python")
class SetupTest(unittest.TestCase):
    def test_registers_private_jobdragonquest_group(self):
        with tempfile.TemporaryDirectory() as tmp:
            os.environ["JOBDRAGONQUEST_CONGO_CONFIG"] = str(Path(tmp) / "congo.env")
            spec = importlib.util.spec_from_file_location("congo_plugin_jobdragonquest", HERE / "jobdragonquest.py")
            plugin = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(plugin)

            async def run():
                client = discord.Client(intents=discord.Intents.none())
                client.tree = app_commands.CommandTree(client)

                class Store:
                    def enabled(self, key):
                        return False

                client.store = Store()
                plugin.setup(client)
                group = client.tree.get_command("jobdragonquest")
                await client.close()
                return group

            group = asyncio.run(run())
            self.assertIsNotNone(group)
            self.assertEqual(sorted(command.name for command in group.commands), ["avisos", "pendencias", "resumo"])
            self.assertFalse(group.allowed_contexts.guild, "não pode aparecer nos servidores")
            self.assertTrue(group.allowed_contexts.dm_channel, "funciona na conversa privada com o bot")
            self.assertFalse(group.allowed_contexts.private_channel)


if __name__ == "__main__":
    unittest.main()
