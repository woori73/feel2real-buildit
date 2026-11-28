import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("start_codi", Path(__file__).with_name("start-codi.py"))
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class StagingLaunchTest(unittest.TestCase):
    def setUp(self):
        self.required = {
            "CODI_DB_HOST": "staging-db.internal",
            "CODI_DB_PASSWORD": "test-only",
            "CODI_JWT_SECRET": "x" * 32,
        }

    def test_inherited_production_settings_are_discarded(self):
        inherited = dict(self.required, SPRING_PROFILES_ACTIVE="prd", SPRING_DATASOURCE_URL="production",
                         JAVA_TOOL_OPTIONS="inherited-options", ANTHROPIC_API_KEY="inherited-key")
        with patch.dict(os.environ, inherited, clear=True), patch.object(os, "execve") as launch:
            launcher.main()
        _, args, env = launch.call_args.args
        self.assertEqual(args[1], "-jar")
        self.assertEqual(env, {
            **self.required, "CODI_DB_PORT": "3306", "PATH": "/usr/bin:/bin", "LANG": "C.UTF-8",
            "AWS_EC2_METADATA_DISABLED": "true", "SPRING_PROFILES_ACTIVE": "codi-staging",
            "SPRING_CONFIG_LOCATION": "classpath:/config/application-codi-staging.yaml",
        })

    def test_enabled_chat_forwards_only_the_systemd_credential_path(self):
        with tempfile.TemporaryDirectory() as directory:
            credential = Path(directory) / "codi-anthropic-key"
            credential.write_text("unit-test-key")
            incoming = dict(self.required, CODI_CHAT_ENABLED="true", CREDENTIALS_DIRECTORY=directory,
                            ANTHROPIC_API_KEY="wrong-key", ANTHROPIC_BASE_URL="https://wrong.invalid",
                            CODI_CHAT_KEY_FILE="/not-a-systemd-credential")
            with patch.dict(os.environ, incoming, clear=True), patch.object(os, "execve") as launch:
                launcher.main()
            env = launch.call_args.args[2]
            self.assertEqual(env["CODI_CHAT_KEY_FILE"], str(credential))
            self.assertEqual(env["CODI_CHAT_ENABLED"], "true")
            self.assertNotIn("ANTHROPIC_API_KEY", env)
            self.assertNotIn("ANTHROPIC_BASE_URL", env)
            self.assertNotIn("unit-test-key", env.values())

    def test_missing_chat_credential_or_invalid_switch_prevents_start(self):
        for values in ({"CODI_CHAT_ENABLED": "true"}, {"CODI_CHAT_ENABLED": "1"},
                       {"CODI_CHAT_ENABLED": "true", "CREDENTIALS_DIRECTORY": "/missing-unit-test-dir"}):
            with self.subTest(values=values), patch.dict(os.environ, {**self.required, **values}, clear=True), \
                    patch.object(os, "execve") as launch, self.assertRaises(SystemExit):
                launcher.main()
            launch.assert_not_called()

    def test_invalid_connection_settings_stop_before_java(self):
        for name, value in (("CODI_DB_HOST", "host/f2r_db?x="), ("CODI_DB_PORT", "0"),
                            ("CODI_DB_PASSWORD", ""), ("CODI_JWT_SECRET", "short")):
            with self.subTest(name=name), patch.dict(os.environ, {**self.required, name: value}, clear=True), \
                    patch.object(os, "execve") as launch, self.assertRaises(SystemExit):
                launcher.main()
            launch.assert_not_called()


if __name__ == "__main__":
    unittest.main()
