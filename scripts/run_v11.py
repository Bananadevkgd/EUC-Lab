from pathlib import Path

# Execute apply_v11.py with one precision fix: the source contains two identical
# decoder.reset()+clearTelemetry pairs, so anchor the v11 connect-time patch to
# reconnectHandler.removeCallbacksAndMessages(null).
path = Path("scripts/apply_v11.py")
text = path.read_text(encoding="utf-8")
old = '''replace_once(
    ble,
    \'\'\'        decoder.reset()\n        WheelRepository.clearTelemetry()\'\'\',
    \'\'\'        decoder.reset()\n        kingSongDecoder.reset()\n        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()\n        kingSongDecoder.setAdvertisedName(currentWheelName)\n        stopKingSongTransport()\n        WheelRepository.clearTelemetry()\'\'\',
)'''
new = '''replace_once(
    ble,
    \'\'\'        reconnectHandler.removeCallbacksAndMessages(null)\n        decoder.reset()\n        WheelRepository.clearTelemetry()\'\'\',
    \'\'\'        reconnectHandler.removeCallbacksAndMessages(null)\n        decoder.reset()\n        kingSongDecoder.reset()\n        currentWheelName = candidateMap[address]?.name ?: rememberedWheelName().orEmpty()\n        kingSongDecoder.setAdvertisedName(currentWheelName)\n        stopKingSongTransport()\n        WheelRepository.clearTelemetry()\'\'\',
)'''
count = text.count(old)
if count != 1:
    raise RuntimeError(f"run_v11.py: expected one ambiguous patch block, got {count}")
fixed = text.replace(old, new, 1)
exec(compile(fixed, str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})
