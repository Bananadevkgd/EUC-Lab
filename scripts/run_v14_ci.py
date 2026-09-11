from pathlib import Path
import runpy

runpy.run_path("scripts/run_v14.py", run_name="__main__")

route = Path("app/src/main/java/com/euclab/app/NativeRouteMapV14.kt")
text = route.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
if "import androidx.compose.foundation.layout.fillMaxSize\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.Box\n",
        "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.fillMaxSize\n",
    )
text = text.replace("Canvas(Modifier.matchParentSize())", "Canvas(Modifier.fillMaxSize())")
route.write_text(text, encoding="utf-8")

decoder = Path("app/src/main/java/com/euclab/app/ble/BegodeFrameDecoderV14.kt")
d = decoder.read_text(encoding="utf-8")
old = 'fun hasResolvedModel(): Boolean = profile != null || model != "Begode"'
new = '''fun hasResolvedModel(): Boolean {
        if (profile != null) return true
        val generic = model.uppercase().replace("_", "").replace(" ", "")
        val gotwaySuffix = generic.substringAfter("GOTWAY", missingDelimiterValue = "")
        val factoryGotwayName = generic.startsWith("BEGODEGOTWAY") ||
            (generic.contains("GOTWAY") && gotwaySuffix.length >= 4 && gotwaySuffix.all { it.isDigit() })
        return model != "Begode" && !factoryGotwayName
    }'''
if old not in d:
    raise RuntimeError("Begode generic-model marker not found")
decoder.write_text(d.replace(old, new, 1), encoding="utf-8")

print("EUC Lab v0.0.14 CI compatibility fixes applied")
