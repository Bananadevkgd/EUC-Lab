from pathlib import Path

# Keep run_v13.py readable while making the generated Kotlin self-contained.
path = Path("scripts/run_v13.py")
text = path.read_text(encoding="utf-8")

# BleWheelManager lives in com.euclab.app.ble, so reference the data tracker by
# its fully-qualified name instead of adding another fragile text import patch.
old = "        SessionTrackerV13.beginFor(address)"
new = "        com.euclab.app.data.SessionTrackerV13.beginFor(address)"
if text.count(old) != 1:
    raise RuntimeError(f"run_v13_ci.py: expected one SessionTrackerV13 insertion, got {text.count(old)}")
text = text.replace(old, new, 1)

# Real Gotway/Begode advertising names also include controller/family prefixes
# that do not contain the marketing brand name. Keep them visible in EUC-only scan.
old_scan = '''            name.contains(\"X-Way\", ignoreCase = true) ||
            name.contains(\"NOSFET\", ignoreCase = true) ||'''
new_scan = '''            name.contains(\"X-Way\", ignoreCase = true) ||
            name.startsWith(\"GW\", ignoreCase = true) ||
            name.contains(\"MCMASTER\", ignoreCase = true) ||
            name.contains(\"MONSTER\", ignoreCase = true) ||
            name.contains(\"MSP\", ignoreCase = true) ||
            name.contains(\"RSHS\", ignoreCase = true) ||
            name.contains(\"EX.N\", ignoreCase = true) ||
            name.contains(\"HERO\", ignoreCase = true) ||
            name.contains(\"NOSFET\", ignoreCase = true) ||'''
if text.count(old_scan) != 1:
    raise RuntimeError(f"run_v13_ci.py: expected one Begode scan insertion point, got {text.count(old_scan)}")
text = text.replace(old_scan, new_scan, 1)

exec(compile(text, str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})
