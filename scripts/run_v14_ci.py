from pathlib import Path
import runpy

runpy.run_path("scripts/run_v14.py", run_name="__main__")

route = Path("app/src/main/java/com/euclab/app/NativeRouteMapV14.kt")
text = route.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
text = text.replace("Canvas(Modifier.matchParentSize())", "Canvas(Modifier.fillMaxSize())")
route.write_text(text, encoding="utf-8")

decoder = Path("app/src/main/java/com/euclab/app/ble/BegodeFrameDecoderV14.kt")
d = decoder.read_text(encoding="utf-8")
old = 'fun hasResolvedModel(): Boolean = profile != null || model != "Begode"'
new = '''fun hasResolvedModel(): Boolean {
        if (profile != null) return true
        val generic = model.uppercase().replace("_", "").replace(" ", "")
        return model != "Begode" && !generic.contains("GOTWAY0") && !generic.matches(Regex(".*GOTWAY\\d{4,}.*"))
    }'''
if old not in d:
    raise RuntimeError("Begode generic-model marker not found")
decoder.write_text(d.replace(old, new, 1), encoding="utf-8")

print("EUC Lab v0.0.14 CI compatibility fixes applied")
