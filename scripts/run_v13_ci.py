from pathlib import Path

# Keep run_v13.py readable while making the generated Kotlin self-contained:
# BleWheelManager lives in com.euclab.app.ble, so reference the data tracker by
# its fully-qualified name instead of adding another fragile text import patch.
path = Path("scripts/run_v13.py")
text = path.read_text(encoding="utf-8")
old = "        SessionTrackerV13.beginFor(address)"
new = "        com.euclab.app.data.SessionTrackerV13.beginFor(address)"
if text.count(old) != 1:
    raise RuntimeError(f"run_v13_ci.py: expected one SessionTrackerV13 insertion, got {text.count(old)}")
fixed = text.replace(old, new, 1)
exec(compile(fixed, str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})
