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


def run_script(*args, mctf_env=True):
    env = {k: v for k, v in os.environ.items() if k not in cm.REQUIRED_ENV}
    env["PYTHONIOENCODING"] = "utf-8"
    if mctf_env:  # placeholders: --dry-run never touches them
        env.update(MCTF_HOME="mctf-home", MCTF_CLI="mctf-home/mctf.py")
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

    def test_events_are_written_compact_in_emission_order(self):
        ev = {"e": "test_result", "suite": "ecore-hooks", "test": "papi-present", "ok": True}
        self.assertEqual(cm.marker_json(ev), '{"e":"test_result","suite":"ecore-hooks","test":"papi-present","ok":true}')
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "events.ndjson"
            cm.write_events(path, [ev, {"e": "suite_end", "failed": 0}])
            self.assertEqual(path.read_text(encoding="utf-8").splitlines()[1], '{"e":"suite_end","failed":0}')

    def test_a_routed_plugin_that_did_not_enable_is_named(self):
        enabled = ["ECoreConformance@3.0.1", "EverNifeCore@3.0.1"]
        self.assertEqual(cm.routed_but_not_enabled(enabled, ["PlaceholderAPI"]), ["PlaceholderAPI"])
        self.assertEqual(cm.routed_but_not_enabled(enabled + ["PlaceholderAPI@2.11.6"], ["PlaceholderAPI"]), [])

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
        self.assertEqual(tools.count("mc_player"), len(cm.SCREENS) + 1)  # + the online check after the restart
        self.assertIn("ecc run all --player", proc.stdout)
        self.assertIn("connect host.docker.internal", proc.stdout)
        self.assertIn("PlaceholderAPI.jar", proc.stdout)
        start_line = next(l for l in proc.stdout.splitlines() if l.startswith("[dry-run] mctf mc_start "))
        self.assertNotIn('"to": "mods"', start_line)  # paper has no mod loader: no companion route

    def test_refuses_without_the_mctf_variables_before_any_call(self):
        proc = run_script("--dry-run", mctf_env=False)
        self.assertEqual(proc.returncode, 2)
        self.assertIn("MCTF_HOME", proc.stderr)
        self.assertIn("MCTF_CLI", proc.stderr)
        self.assertNotIn("[dry-run]", proc.stdout)


if __name__ == "__main__":
    unittest.main()
