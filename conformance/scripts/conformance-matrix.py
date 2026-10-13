"""Runs the ECoreConformance suites across the mctf server matrix and writes a summary per flavor.

Every server/client interaction goes through the mctf CLI (`python mctf.py <tool> key:=json ...`),
one call per step. Per flavor: boot with the core + ECoreConformance + harness routed, join a
headless bot, run/write the suites, open every GUI screen on both sides, restart (persistence
proof), read the suites back, collect the ENCTEST markers via mc_read_events and the plugin's own
NDJSON reports, tear down. A flavor that dies mid-way is recorded as failed and the next one runs.

Exit code 0 only when every flavor booted, nothing went wrong on the way, and every suite_end
reports failed=0.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parents[2]
# Copied there by :conformance:minecraft:shadowJar from a public repository (version in libs.versions.toml)
DEFAULT_PAPI = str(REPO_ROOT / "conformance/minecraft/build/matrix/PlaceholderAPI.jar")
# The mctf CLI and the framework root (its final-artifacts/ carries the companion mods); machine-local paths
REQUIRED_ENV = ("MCTF_HOME", "MCTF_CLI")
DEFAULT_FLAVORS = "crucible-1.7.10,mohist-1.12.2,mohist-1.16.5,arclight-1.21.1,paper-1.21.1"

CLIENT_FOR = {
    "crucible-1.7.10": "forge-1.7.10",
    "mohist-1.12.2": "forge-1.12.2",
    "mohist-1.16.5": "forge-1.16.5",
    "arclight-1.21.1": "neoforge-1.21.1",
    "paper-1.21.1": "neoforge-1.21.1",
}
HYBRIDS = {"crucible-1.7.10", "mohist-1.12.2", "mohist-1.16.5", "arclight-1.21.1"}
SCREENS = ["basic", "hopper", "dispenser", "workbench", "nav", "storage", "prompt", "pager",
           "title-long", "layout", "confirm"]
SUITE_ORDER = ["items", "nbt", "platform", "version", "commands", "gui", "scheduler", "playerdata",
               "events", "selection-click", "particles", "text-metrics", "hooks", "nms", "regions"]
HALVES = ("run", "write", "read")
EXPECTED_ENDS = {"run": 14, "write": 3, "read": 3}
WAIT_SECONDS = {"run": 420, "write": 180, "read": 180}
TEXT_LINES = ("iiiiiiiiii", "WWWWWWWWWW")
BOT = "EccBot"
PLUGIN_ID = "ECoreConformance@3.0.1"


class FlavorFailed(Exception):
    pass


@dataclass
class FlavorResult:
    flavor: str
    booted: bool = False
    events: List[dict] = field(default_factory=list)
    gui: List[dict] = field(default_factory=list)
    text: List[dict] = field(default_factory=list)
    shutdown_reason: Optional[str] = None
    problems: List[str] = field(default_factory=list)


# ------------------------------------------------------------------ pure parts (tested offline) --

def load_ndjson(path) -> List[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def marker_json(ev: dict) -> str:
    """One event as the plugin's own marker writes it: compact, keys in emission order, so a grep for
    '"e":"suite_end"' reads events.ndjson the same way it reads the server log."""
    return json.dumps(ev, separators=(",", ":"))


def write_events(path, events: List[dict]):
    with open(path, "w", encoding="utf-8") as f:
        f.writelines(marker_json(ev) + "\n" for ev in events)


def half_of(cid: Optional[str]) -> str:
    """The driver mints cids as <prefix>-<half>; anything else is read as a run half."""
    tail = (cid or "").rsplit("-", 1)[-1]
    return tail if tail in HALVES else "run"


def suite_table(events: List[dict]) -> Dict[str, Dict[str, dict]]:
    """{suite: {half: {passed, failed, skipped}}} from the suite_end markers; suite ids lose 'ecore-'."""
    table: Dict[str, Dict[str, dict]] = {}
    for ev in events:
        if ev.get("e") != "suite_end":
            continue
        suite = str(ev.get("suite", "?"))
        suite = suite[len("ecore-"):] if suite.startswith("ecore-") else suite
        cell = table.setdefault(suite, {}).setdefault(half_of(ev.get("cid")), {"passed": 0, "failed": 0, "skipped": 0})
        for key in cell:
            cell[key] += int(ev.get(key) or 0)
    return table


def routed_but_not_enabled(enabled: List[str], routed: List[str]) -> List[str]:
    """Plugin names routed into the server that did not come up - a broken fixture, not a suite verdict."""
    up = {e.split("@", 1)[0] for e in enabled}
    return [name for name in routed if name not in up]


def flavor_ok(result: FlavorResult) -> bool:
    if not result.booted or result.problems:
        return False
    return all(ev.get("failed", 0) == 0 for ev in result.events if ev.get("e") == "suite_end")


def exit_code(results: List[FlavorResult]) -> int:
    return 0 if results and all(flavor_ok(r) for r in results) else 1


def _ordered_suites(tables: List[dict]) -> List[str]:
    seen = {s for t in tables for s in t}
    return [s for s in SUITE_ORDER if s in seen] + sorted(seen - set(SUITE_ORDER))


def _cell(halves: Optional[dict]) -> str:
    if not halves:
        return "-"
    return ", ".join("%s %d/%d/%d" % (h, c["passed"], c["failed"], c["skipped"])
                     for h, c in sorted(halves.items(), key=lambda kv: HALVES.index(kv[0])))


def render_flavor_section(r: FlavorResult) -> str:
    out = ["## %s - %s" % (r.flavor, "OK" if flavor_ok(r) else "FAILED"), ""]
    out.append("- booted: %s" % r.booted)
    out.append("- restart shutdown_reason: %s" % (r.shutdown_reason or "n/a"))
    for p in r.problems:
        out.append("- problem: %s" % p)
    papi = [ev for ev in r.events if ev.get("e") == "test_result" and ev.get("test") == "papi-present"]
    if papi:
        out.append("- papi-present: %s" % ("passed" if all(ev.get("ok") for ev in papi) else "FAILED"))
    out += ["", "### Suites (passed/failed/skipped)", ""]
    table = suite_table(r.events)
    for suite in _ordered_suites([table]):
        out.append("- %s: %s" % (suite, _cell(table.get(suite))))
    failed = [ev for ev in r.events if ev.get("e") == "test_result" and ev.get("ok") is False]
    if failed:
        out += ["", "### Failed cases", ""]
        out += ["- %s / %s: %s" % (ev.get("suite"), ev.get("test"), ev.get("error")) for ev in failed]
    if r.gui:
        out += ["", "### GUI screens", ""]
        out += ["- %s: %s" % (g["screen"], g["line"]) for g in r.gui]
    out += ["", "### Text render evidence", ""]
    if r.text:
        out += ["- x=%s y=%s text=%r" % (e.get("x"), e.get("y"), e.get("text")) for e in r.text]
    else:
        out.append("- no drawn entry carried %s" % " / ".join(TEXT_LINES))
    return "\n".join(out) + "\n"


def render_summary(results: List[FlavorResult]) -> str:
    tables = {r.flavor: suite_table(r.events) for r in results}
    flavors = [r.flavor for r in results]
    out = ["# ECoreConformance matrix", "",
           "Verdict: %s" % ("PASS" if exit_code(results) == 0 else "FAIL"), "",
           "Cells: half passed/failed/skipped (run = `ecc run`, write/read = the halves around the restart).", "",
           "| suite | " + " | ".join(flavors) + " |",
           "|---|" + "---|" * len(flavors)]
    for suite in _ordered_suites(list(tables.values())):
        out.append("| %s | " % suite + " | ".join(_cell(tables[f].get(suite)) for f in flavors) + " |")
    out.append("")
    for r in results:
        out.append(render_flavor_section(r))
    return "\n".join(out)


def gui_line(snapshot: dict, render: dict) -> str:
    """'server: N slots / client: N slots' - or the client's capability_warning / missing capture."""
    snap = snapshot.get("snapshot") or {}
    server = "server: %d filled slots (%s)" % (len(snap.get("open_container") or []), snap.get("container_type"))
    if render.get("capability_warning"):
        client = "client: capability_warning %s" % render["capability_warning"]
    elif not render.get("ok", True):
        client = "client: error %s" % (render.get("error") or {}).get("code")
    elif not render.get("captured"):
        client = "client: not captured"
    else:
        slots = render.get("slots") or []
        filled = sum(1 for s in slots if s.get("item") not in (None, "", "air", "minecraft:air"))
        client = "client: %d slots (%d filled, screen %s)" % (len(slots), filled, render.get("screen"))
    return server + " / " + client


# --------------------------------------------------------------------------------- mctf calls --

DRY_RESULT = {
    "ok": True, "server_id": "<server_id>", "client_id": "<client_id>", "run_dir": None,
    "overrides": [], "plugins": {"enabled": [PLUGIN_ID]}, "linkage_errors": 0,
    "milestones": {"joined": True}, "mapped_game_port": "<mapped_game_port>",
    "shutdown_reason": "<dry-run>", "events": [], "has_more": False,
    "reply": {"ok": True, "x": 0.5, "y": 65.62, "z": 0.5, "hit": "block"},
}


class Mctf:
    def __init__(self, cli: str, dry_run: bool):
        self.cli = cli
        self.dry_run = dry_run

    def __call__(self, tool: str, **args) -> dict:
        if self.dry_run:
            print("[dry-run] mctf %s %s" % (tool, json.dumps(args, sort_keys=True)), flush=True)
            return dict(DRY_RESULT)
        cmd = [sys.executable, self.cli, tool] + ["%s:=%s" % (k, json.dumps(v)) for k, v in args.items()]
        env = dict(os.environ, PYTHONIOENCODING="utf-8")
        proc = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", env=env, timeout=3600)
        try:
            return json.loads(proc.stdout)
        except ValueError:
            return {"ok": False, "error": {"code": "NO_JSON", "message": (proc.stdout + proc.stderr)[-2000:]}}

    def sleep(self, seconds: float):
        if not self.dry_run:
            time.sleep(seconds)


def _err(res: dict) -> str:
    e = res.get("error") or {}
    return "%s: %s" % (e.get("code"), e.get("message"))


def read_all_events(mctf: Mctf, server_id: str, event_filter: Optional[str] = None) -> List[dict]:
    events, cursor = [], None
    while True:
        args = {"server_id": server_id, "max_events": 200}
        if cursor:
            args["cursor"] = cursor
        if event_filter:
            args["event_filter"] = event_filter
        res = mctf("mc_read_events", **args)
        if not res.get("ok"):
            raise FlavorFailed("mc_read_events: " + _err(res))
        events += res.get("events") or []
        cursor = res.get("next_cursor")
        if not res.get("has_more") or not cursor:
            return events


def wait_suite_ends(mctf: Mctf, server_id: str, cid: str, r: FlavorResult):
    half = half_of(cid)
    want, deadline = EXPECTED_ENDS[half], time.time() + WAIT_SECONDS[half]
    while True:
        got = sum(1 for ev in read_all_events(mctf, server_id, "suite_end") if ev.get("cid") == cid)
        if got >= want or mctf.dry_run:
            return
        if time.time() > deadline:
            r.problems.append("%s: %d of %d suite_end within %ds" % (half, got, want, WAIT_SECONDS[half]))
            return
        mctf.sleep(5)


def merge_events(*batches: List[dict]) -> List[dict]:
    seen, out = set(), []
    for batch in batches:
        for ev in batch:
            key = json.dumps(ev, sort_keys=True)
            if key not in seen:
                seen.add(key)
                out.append(ev)
    return out


# ------------------------------------------------------------------------------- per flavor --

def console(mctf: Mctf, server_id: str, command: str) -> dict:
    res = mctf("mc_exec", server_id=server_id, command=command, correlate=False)
    if not res.get("ok"):
        raise FlavorFailed("mc_exec %r: %s" % (command, _err(res)))
    return res


def bot(mctf: Mctf, client_id: str, command: str, **extra) -> dict:
    return mctf("mc_hmc_exec", client_id=client_id, command=command, **extra)


def wait_online(mctf: Mctf, server_id: str, seconds: int = 120):
    """A reconnect answers once the client starts joining; the server logs the player in seconds later, and a
    command naming him before that finds nobody."""
    deadline = time.time() + seconds
    while True:
        snap = mctf("mc_player", server_id=server_id, player=BOT).get("snapshot") or {}
        if snap.get("online") or mctf.dry_run:
            return
        if time.time() > deadline:
            raise FlavorFailed("%s not online %ds after the reconnect" % (BOT, seconds))
        mctf.sleep(3)


def click_a_block(mctf: Mctf, client_id: str, r: FlavorResult):
    """Wand in hand (slot 0), look straight down at the block under the feet, left-click it."""
    bot(mctf, client_id, "/mctf slot 0")
    pos = (bot(mctf, client_id, "/mctf pos").get("reply") or {})
    if not pos.get("ok"):
        r.problems.append("/mctf pos failed: %s" % pos)
        return
    feet = float(pos["y"]) - 1.62  # pos reports the eyes
    bot(mctf, client_id, "/mctf look %.2f %.2f %.2f" % (float(pos["x"]), feet - 0.5, float(pos["z"])))
    hit = bot(mctf, client_id, "/mctf attack 300").get("reply") or {}
    if hit.get("hit") != "block":
        r.problems.append("attack did not hit a block: %s" % hit)


def run_flavor(flavor: str, a, mctf: Mctf, out: Path, fw: Path) -> FlavorResult:
    r = FlavorResult(flavor)
    mc = flavor.split("-", 1)[1]
    fdir = out / flavor
    server_id = client_id = None
    stamp = time.strftime("%H%M%S")
    cid = {h: "ecc%s-%s" % (stamp, h) for h in HALVES}
    pre_restart: List[dict] = []
    try:
        routes = [{"from": [a.core_jar], "to": "plugins"}, {"from": [a.ecc_jar], "to": "plugins"}]
        if flavor in a.papi_flavors:
            routes.append({"from": [a.papi_jar], "to": "plugins"})
        if flavor in HYBRIDS:
            routes.append({"from": [str(fw / "final-artifacts/companion" / ("MCTFCompanion-%s-*.jar" % mc))], "to": "mods"})
        routes.append({"from": [str(fw / "final-artifacts/harness/ENCTestHarness-*.jar")], "to": "plugins"})

        start = mctf("mc_start", flavor=flavor, routes=routes, project_root=str(REPO_ROOT), suite="conformance")
        server_id = start.get("server_id")
        if not start.get("ok"):
            raise FlavorFailed("mc_start: " + _err(start))
        if start.get("overrides"):
            raise FlavorFailed("mc_start overrides (a route shadowed another): %s" % start["overrides"])
        enabled = (start.get("plugins") or {}).get("enabled") or []
        if PLUGIN_ID not in enabled:
            raise FlavorFailed("%s not enabled; plugins=%s" % (PLUGIN_ID, start.get("plugins")))
        dead = routed_but_not_enabled(enabled, ["PlaceholderAPI"] if flavor in a.papi_flavors else [])
        if dead:
            r.problems.append("routed but not enabled: %s; not_enabled=%s" % (dead, (start.get("plugins") or {}).get("not_enabled")))
        if start.get("linkage_errors"):
            r.problems.append("linkage_errors=%s at boot" % start["linkage_errors"])
        r.booted = True
        run_dir = start.get("run_dir")

        client = mctf("mc_hmc_start", target_id=CLIENT_FOR[flavor], server_id=server_id, username=BOT,
                      project_root=str(REPO_ROOT), suite="conformance",
                      routes=[{"from": [str(fw / "final-artifacts/companion" / ("MCTFCompanion-%s-*.jar" % mc))], "to": "mods"}])
        client_id = client.get("client_id")
        joined = (client.get("milestones") or {}).get("joined") or (client.get("screen_check") or {}).get("in_world")
        if not client.get("ok") or not joined:
            raise FlavorFailed("mc_hmc_start did not join: %s %s" % (_err(client), client.get("reject_reason")))
        console(mctf, server_id, "op " + BOT)
        console(mctf, server_id, "gamemode creative " + BOT)

        console(mctf, server_id, "ecc click " + BOT)
        click_a_block(mctf, client_id, r)
        console(mctf, server_id, "ecc run all --player %s --cid %s" % (BOT, cid["run"]))
        wait_suite_ends(mctf, server_id, cid["run"], r)
        console(mctf, server_id, "ecc write all --player %s --cid %s" % (BOT, cid["write"]))
        wait_suite_ends(mctf, server_id, cid["write"], r)

        for screen in SCREENS:
            console(mctf, server_id, "ecc gui %s %s" % (screen, BOT))
            mctf.sleep(2)  # screens open on a later tick
            snap = mctf("mc_player", server_id=server_id, player=BOT)
            render = mctf("mc_hmc_render", client_id=client_id, mode="gui")
            bot(mctf, client_id, "close")
            r.gui.append({"screen": screen, "line": gui_line(snap, render)})
            if not mctf.dry_run:
                (fdir / "gui").mkdir(parents=True, exist_ok=True)
                for suffix, payload in (("server", snap), ("client", render)):
                    with open(fdir / "gui" / ("%s.%s.json" % (screen, suffix)), "w", encoding="utf-8") as f:
                        json.dump(payload, f, indent=2)

        console(mctf, server_id, "ecc text " + BOT)
        drawn = mctf("mc_hmc_render", client_id=client_id, mode="render", duration_ms=2000)
        r.text = [e for e in drawn.get("entries") or [] if any(t in str(e.get("text")) for t in TEXT_LINES)]

        pre_restart = read_all_events(mctf, server_id)
        restart = mctf("mc_restart", server_id=server_id)
        r.shutdown_reason = "%s (graceful_shutdown=%s)" % (restart.get("shutdown_reason"), restart.get("graceful_shutdown"))
        if not restart.get("ok"):
            raise FlavorFailed("mc_restart: " + _err(restart))
        reconnect = bot(mctf, client_id, "connect host.docker.internal %s" % restart.get("mapped_game_port"), timeout_seconds=120)
        if not reconnect.get("ok"):
            raise FlavorFailed("bot reconnect after restart: " + _err(reconnect))
        wait_online(mctf, server_id)
        console(mctf, server_id, "op " + BOT)
        console(mctf, server_id, "gamemode creative " + BOT)
        console(mctf, server_id, "ecc read all --player %s --cid %s" % (BOT, cid["read"]))
        wait_suite_ends(mctf, server_id, cid["read"], r)

        r.events = merge_events(pre_restart, read_all_events(mctf, server_id))
        if not mctf.dry_run and run_dir:
            reports = Path(run_dir) / "plugins/ECoreConformance/reports"
            (fdir / "reports").mkdir(parents=True, exist_ok=True)
            for report in reports.glob("*.ndjson"):
                shutil.copy2(report, fdir / "reports" / report.name)
    except FlavorFailed as e:
        r.problems.append(str(e))
        r.events = r.events or pre_restart
    finally:
        if not a.keep:
            if client_id:
                mctf("mc_hmc_stop", client_id=client_id)
            if server_id:
                mctf("mc_stop", server_id=server_id)
    sel = suite_table(r.events).get("selection-click", {}).get("run")
    if r.booted and sel and sel["skipped"]:
        r.problems.append("selection-click was skipped - the bot's block click never armed/arrived")
    if not mctf.dry_run:
        fdir.mkdir(parents=True, exist_ok=True)
        write_events(fdir / "events.ndjson", r.events)
        with open(fdir / "summary.md", "w", encoding="utf-8") as f:
            f.write(render_flavor_section(r))
    return r


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Run the ECoreConformance suites across the mctf matrix.")
    p.add_argument("--flavors", default=DEFAULT_FLAVORS, help="comma-separated mctf flavors")
    p.add_argument("--core-jar", default=str(REPO_ROOT / "minecraft/build/libs/EverNifeCore-3.0.1.jar"))
    p.add_argument("--ecc-jar", default=str(REPO_ROOT / "conformance/minecraft/build/libs/ECoreConformance-3.0.1.jar"))
    p.add_argument("--papi-jar", default=DEFAULT_PAPI, help="PlaceholderAPI jar routed into --papi-flavors")
    p.add_argument("--papi-flavors", default="paper-1.21.1", help="comma-separated flavors that get PlaceholderAPI")
    p.add_argument("--out", default="build/conformance", help="output dir, relative to the repo root")
    p.add_argument("--keep", action="store_true", help="leave the servers and clients running")
    p.add_argument("--dry-run", action="store_true", help="print the planned mctf calls, call nothing, write nothing")
    a = p.parse_args(argv)
    a.papi_flavors = {f.strip() for f in a.papi_flavors.split(",") if f.strip()}
    flavors = [f.strip() for f in a.flavors.split(",") if f.strip()]
    unknown = [f for f in flavors if f not in CLIENT_FOR]
    if unknown:
        p.error("no client pair for %s - known: %s" % (unknown, ", ".join(CLIENT_FOR)))

    unset = [k for k in REQUIRED_ENV if not os.environ.get(k)]
    if unset:
        print("%s not set. Define MCTF_HOME (the mctf framework root) and MCTF_CLI (its mctf.py) to run the\n"
              "matrix. It is a local tool of the project owner and needs the mctf daemon; the regular\n"
              "Gradle build and tests do not need it." % " and ".join(unset), file=sys.stderr)
        return 2
    cli = os.environ["MCTF_CLI"]
    fw = Path(os.environ["MCTF_HOME"])
    if not a.dry_run:
        needed = [a.core_jar, a.ecc_jar, cli] + ([a.papi_jar] if a.papi_flavors & set(flavors) else [])
        missing = [n for n in needed if not Path(n).is_file()]
        if missing:
            print("missing: %s (the jars come from ./gradlew :minecraft:shadowJar :conformance:minecraft:shadowJar)"
                  % ", ".join(missing), file=sys.stderr)
            return 2
    out = Path(a.out) if Path(a.out).is_absolute() else REPO_ROOT / a.out
    mctf = Mctf(cli, a.dry_run)

    results = [run_flavor(f, a, mctf, out, fw) for f in flavors]
    if not a.dry_run:
        with open(out / "summary.md", "w", encoding="utf-8") as f:
            f.write(render_summary(results))
        print(render_summary(results))
    return exit_code(results) if not a.dry_run else 0


if __name__ == "__main__":
    sys.exit(main())
