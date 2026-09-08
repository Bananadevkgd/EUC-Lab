from pathlib import Path
import runpy


# v0.0.9 is layered on top of the field-tested v0.0.8 patch.
runpy.run_path("scripts/apply_v8.py", run_name="__main__")


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


wheel = "app/src/main/java/com/euclab/app/WheelScreenV7.kt"

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Dynamic Assist", "Dynamic Assist"), s.dynamicAssist, 0, 100, "%", stopped && online, tr(ru, "Динамическая помощь LeaperKim. Меняет характер поддержки при разгоне/нагрузке. Меняй постепенно и тестируй на малой скорости.", "LeaperKim dynamic assist. Changes the feel under acceleration/load. Adjust gradually and test at low speed."), { info = it }) { result = commandResult(ru, ble.setDynamicAssist(it)) } }''',
    '''item { V7SliderSetting(tr(ru, "Ассистент разгона и торможения", "Acceleration / braking assist"), s.dynamicAssist, 0, 100, "%", stopped && online, tr(ru, "Степень помощи райдеру при разгоне и торможении. Чем выше значение, тем заметнее ассистирование. Не увеличивает максимальную мощность двигателя напрямую. Меняй постепенно и сначала проверяй на малой скорости.", "How strongly the wheel assists the rider during acceleration and braking. Higher values make the assistance more noticeable. This does not directly increase maximum motor power. Change gradually and test at low speed first."), { info = it }) { result = commandResult(ru, ble.setDynamicAssist(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Ограничение ускорения", "Acceleration limit"), s.accelerationLimit, 0, 100, "%", stopped && online, tr(ru, "Ограничивает агрессивность разгона в логике контроллера. Это не лимит максимальной скорости.", "Limits acceleration aggressiveness in the controller. It is not a top-speed limit."), { info = it }) { result = commandResult(ru, ble.setAccelerationLimit(it)) } }''',
    '''item { V7SliderSetting(tr(ru, "Снижение резкости разгона", "Acceleration reduction"), s.accelerationLimit, 0, 100, "%", stopped && online, tr(ru, "Сглаживает реакцию колеса на разгон. Это не ограничитель максимальной скорости. Точный внутренний алгоритм LeaperKim не документирует, поэтому меняй постепенно и проверяй поведение на малой скорости.", "Softens the wheel response to acceleration. This is not a top-speed limiter. LeaperKim does not document the exact internal algorithm, so change it gradually and test at low speed."), { info = it }) { result = commandResult(ru, ble.setAccelerationLimit(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7ToggleSetting("High Speed Mode", s.highSpeedMode, stopped && online, tr(ru, "Режим высокой скорости меняет рабочие ограничения колеса. Используй только если понимаешь влияние на запас по напряжению/PWM.", "High Speed Mode changes the wheel's operating limits. Use only if you understand the effect on voltage/PWM margin."), { info = it }) { result = commandResult(ru, ble.setHighSpeedMode(it)) } }''',
    '''item { V7ToggleSetting(tr(ru, "Высокоскоростной режим · ослабление поля", "High-speed mode · field weakening"), s.highSpeedMode, stopped && online, tr(ru, "Расширяет доступный диапазон оборотов двигателя за счёт ослабления поля. На высокой скорости колесо позже упирается в предел по PWM/оборотам, но запас тяги уменьшается. Это не режим «больше мощности»: на высокой скорости избегай резких ускорений.", "Extends the motor speed range using field weakening. At high speed the wheel reaches its PWM/RPM ceiling later, but torque reserve is reduced. This is not a 'more power' mode: avoid hard acceleration at high speed."), { info = it }, danger = true) { result = commandResult(ru, ble.setHighSpeedMode(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7ToggleSetting("Low Voltage Mode", s.lowVoltageMode, stopped && online, tr(ru, "Позволяет использовать более глубокую часть разряда. При низком напряжении запас мощности уменьшается — это настройка повышенного риска.", "Allows use of a deeper discharge region. Power margin falls at low voltage; this is a higher-risk setting."), { info = it }) { result = commandResult(ru, ble.setLowVoltageMode(it)) } }''',
    '''item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1115)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.border(1.dp, V7Danger.copy(alpha = .75f), RoundedCornerShape(22.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(ru, "⚠ РЕЖИМ ГЛУБОКОГО РАЗРЯДА", "⚠ DEEP-DISCHARGE MODE"), color = V7Danger, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            tr(ru,
                                "Аварийный запас, чтобы добраться до зарядки. Не включай просто так: при низком напряжении меньше запас мощности, а частое использование глубокого разряда ускоряет износ АКБ. После поездки не оставляй колесо глубоко разряженным — после короткой паузы/остывания поставь его на зарядку.",
                                "Emergency reserve intended to reach a charger. Do not enable casually: low voltage reduces power margin and repeated deep discharge accelerates battery wear. Do not leave the wheel deeply discharged after the ride; after a short cool-down, put it on charge."
                            ),
                            color = Color(0xFFFFC7CC), fontSize = 10.sp, lineHeight = 15.sp,
                        )
                    }
                }
            }
            item { V7ToggleSetting(tr(ru, "Режим глубокого разряда / аварийный запас", "Deep-discharge / emergency reserve"), s.lowVoltageMode, stopped && online, tr(ru, "Сдвигает штатные пороги низкого напряжения ниже и позволяет использовать более глубокую часть заряда. Использовать только как аварийный запас до зарядки. После поездки не хранить колесо глубоко разряженным.", "Moves the wheel's low-voltage thresholds lower and allows deeper discharge. Use only as emergency reserve to reach a charger. Do not store the wheel deeply discharged after the ride."), { info = it }, danger = true) { result = commandResult(ru, ble.setLowVoltageMode(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Лимит PWM · RAW", "PWM limit · RAW"), s.pwmLimitRaw, 0, 100, "", stopped && online, tr(ru, "Сохраняем и отправляем именно значение протокола. Пока не показываем пересчитанный процент, чтобы не выдать неверную трактовку за факт.", "The raw protocol value is shown and sent. We intentionally do not relabel it as a percentage until the conversion is fully verified."), { info = it }, danger = true) { result = commandResult(ru, ble.setPwmLimitRaw(it)) } }''',
    '''item { V7SliderSetting(tr(ru, "PWM-лимит подъёма педалей", "PWM tilt-back limit"), s.pwmLimitRaw, 30, 100, "%", stopped && online, tr(ru, "Порог PWM (Stop Power Rate), при котором колесо начинает защитный подъём педалей. Чем ниже значение, тем раньше сработает tilt-back. Не путать со звуковыми PWM-предупреждениями EUC Lab — они настраиваются отдельно.", "PWM threshold (Stop Power Rate) at which the wheel begins protective pedal tilt-back. Lower values trigger tilt-back earlier. This is separate from EUC Lab's own PWM sound alarms."), { info = it }, danger = true) { result = commandResult(ru, ble.setPwmLimitRaw(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7ToggleSetting(tr(ru, "Транспортировочный режим", "Transport mode"), s.transportMode, stopped && online, tr(ru, "Режим транспортировки блокирует тягу для безопасной перевозки. Не включай во время движения.", "Transport mode disables drive torque for transport. Never enable while moving."), { info = it }, danger = true) { result = commandResult(ru, ble.setTransportMode(it)) } }''',
    '''item { V7ToggleSetting(tr(ru, "Режим транспортировки / Sleep Mode", "Transport / Sleep Mode"), s.transportMode, stopped && online, tr(ru, "Блокирует обычное включение колеса при перевозке. На поддерживаемых новых LeaperKim для выхода из режима нужно удерживать кнопку POWER около 10 секунд до сигнала либо подключить зарядное устройство. На более старых моделях способ выхода может отличаться.", "Prevents normal wheel power-up during transport. On supported newer LeaperKim wheels, exit by holding POWER for about 10 seconds until the tone, or connect the charger. Older models may use a different exit procedure."), { info = it }, danger = true) { result = commandResult(ru, ble.setTransportMode(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7SectionTitle(tr(ru, "Защиты и калибровка", "Protection & calibration")) }''',
    '''item { V7SectionTitle(tr(ru, "Защиты и калибровка", "Protection & calibration")) }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF241B0D)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.border(1.dp, V7Amber.copy(alpha = .65f), RoundedCornerShape(22.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(ru, "⚠ СЕРВИСНЫЕ ПАРАМЕТРЫ", "⚠ SERVICE PARAMETERS"), color = V7Amber, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(tr(ru, "Коррекцию напряжения и лимит зарядного напряжения не меняй без измерений и понимания точного значения. Неверная настройка может сделать показания заряда некорректными или изменить режим зарядки.", "Do not change voltage calibration or charge-voltage limit without reference measurements and understanding the exact value. Incorrect settings can make battery readings wrong or alter charging behaviour."), color = Color(0xFFFFDDA0), fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }''',
)

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Коррекция напряжения", "Voltage correction"), s.voltageCorrection, -15, 15, "", stopped && online, tr(ru, "Сервисная поправка измерения напряжения. Без контрольного мультиметра лучше не менять.", "Service correction for voltage measurement. Do not change without a reference meter."), { info = it }, danger = true) { result = commandResult(ru, ble.setVoltageCorrection(it)) } }''',
    '''item { V7SliderSetting(tr(ru, "Калибровка напряжения · СЕРВИС", "Voltage calibration · SERVICE"), s.voltageCorrection, -15, 15, "", stopped && online, tr(ru, "Сервисная коррекция измерения напряжения. Не менять без точного эталонного измерения мультиметром. Ошибочное значение исказит показания напряжения и расчёт заряда.", "Service calibration of voltage measurement. Do not change without an accurate reference multimeter. An incorrect value will distort voltage and battery estimates."), { info = it }, danger = true) { result = commandResult(ru, ble.setVoltageCorrection(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Макс. напряжение заряда · RAW", "Max charge voltage · RAW"), s.maxChargeVoltageRaw, 0, 120, "", stopped && online, tr(ru, "Сервисное значение протокола зарядного лимита. Оставлено RAW, пока формула полного напряжения для конкретной модели не подтверждена на железе.", "Raw protocol charge-voltage limit. Kept RAW until the model-specific full-voltage conversion is verified on hardware."), { info = it }, danger = true) { result = commandResult(ru, ble.setMaxChargeVoltageRaw(it)) } }''',
    '''item { V7SliderSetting(tr(ru, "Лимит напряжения зарядки · СЕРВИС", "Charge-voltage limit · SERVICE"), s.maxChargeVoltageRaw, 0, 120, "", stopped && online, tr(ru, "Меняет верхний лимит завершения зарядки. Не трогай без необходимости. Сейчас EUC Lab сохраняет протокольное значение без выдуманного пересчёта в вольты; реальное отображение в V будет добавлено только после точной модельной привязки.", "Changes the upper charge-completion voltage limit. Do not adjust casually. EUC Lab currently preserves the protocol value without inventing a voltage conversion; real volts will be shown only after exact model-specific mapping is verified."), { info = it }, danger = true) { result = commandResult(ru, ble.setMaxChargeVoltageRaw(it)) } }''',
)

replace_once(
    wheel,
    '''item { V7SliderSetting(tr(ru, "Сигнал давления торможения", "Brake pressure alarm"), s.brakePressureAlarm, 0, 150, "", stopped && online, tr(ru, "Порог штатного предупреждения при интенсивном торможении/нагрузке. Значение протокола отображается напрямую.", "Threshold for the wheel's built-in hard-braking/load warning. Protocol value is shown directly."), { info = it }) { result = commandResult(ru, ble.setBrakePressureAlarm(it)) } }''',
    '''// Brake Pressure Alarm intentionally hidden: command/readback are known, but the physical meaning of its percentage is not documented well enough for a safe user-facing control.''',
)

ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_all(ui, "v0.0.8", "v0.0.9")

gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 8", "        versionCode = 9")
replace_once(gradle, '        versionName = "0.0.8"', '        versionName = "0.0.9"')
replace_all(gradle, "// v0.0.8:", "// v0.0.9:")

print("EUC Lab v0.0.9 source patch applied")
