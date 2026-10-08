import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "conformance-matrix.py"

spec = importlib.util.spec_from_file_location("conformance_matrix", SCRIPT)
cm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cm)


def run_script(*args):
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], capture_output=True,
                          encoding="utf-8", errors="replace", env=env, timeout=60)


class ParsingTest(unittest.TestCase):

    def setUp(self):
        self.events = cm.load_ndjson(HERE / "testdata" / "events-sample.ndjson")

    def test_table_splits_suites_by_half(self):
        self.assertEqual(cm.suite_table(self.events), {
            "items": {"run": {"passed": 2, "failed": 0, "skipped": 0}},
            "hooks": {"run": {"passed": 1, "failed": 1, "skipped": 1}},
            "nbt": {"write": {"passed": 1, "failed": 0, "skipped": 0},
                    "read": {"passed": 1, "failed": 0, "skipped": 0}},
        })

    def test_failed_suite_fails_the_verdict(self):
        self.assertEqual(cm.exit_code([cm.FlavorResult("paper-1.21.1", booted=True, events=self.events)]), 1)

    def test_clean_run_passes_and_unbooted_flavor_fails(self):
        clean = [ev for ev in self.events if ev.get("suite") != "ecore-hooks"]
        self.assertEqual(cm.exit_code([cm.FlavorResult("paper-1.21.1", booted=True, events=clean)]), 0)
        self.assertEqual(cm.exit_code([cm.FlavorResult("paper-1.21.1", booted=True, events=clean),
                                       cm.FlavorResult("mohist-1.12.2")]), 1)

    def test_summary_carries_matrix_and_restart_reason(self):
        r = cm.FlavorResult("paper-1.21.1", booted=True, events=self.events,
                            shutdown_reason="rcon_stop (graceful_shutdown=True)")
        text = cm.render_summary([r])
        self.assertIn("| hooks | run 1/1/1 |", text)
        self.assertIn("| nbt | write 1/0/0, read 1/0/0 |", text)
        self.assertIn("rcon_stop", text)
        self.assertIn("papi-present: FAILED", text)


class CliTest(unittest.TestCase):

    def test_help_exits_zero(self):
        self.assertEqual(run_script("--help").returncode, 0)

    def test_dry_run_plans_calls_in_order_and_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "out"
            proc = run_script("--dry-run", "--flavors", "paper-1.21.1", "--out", str(out))
            self.assertEqual(proc.returncode, 0, proc.stderr)
            self.assertFalse(out.exists())
        tools = re.findall(r"^\[dry-run\] mctf (\S+)", proc.stdout, re.M)
        key = [t for t in tools if t in ("mc_start", "mc_hmc_start", "mc_restart", "mc_hmc_stop", "mc_stop")]
        self.assertEqual(key, ["mc_start", "mc_hmc_start", "mc_restart", "mc_hmc_stop", "mc_stop"])
        self.assertEqual(tools.count("mc_player"), len(cm.SCREENS))
        self.assertIn("ecc run all --player", proc.stdout)
        self.assertIn("connect host.docker.internal", proc.stdout)
        self.assertIn("PlaceholderAPI-2.11.2.jar", proc.stdout)
        start_line = next(l for l in proc.stdout.splitlines() if l.startswith("[dry-run] mctf mc_start "))
        self.assertNotIn('"to": "mods"', start_line)  # paper has no mod loader: no companion route


if __name__ == "__main__":
    unittest.main()
