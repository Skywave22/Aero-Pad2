import re

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'r') as f:
    text = f.read()

execute_action_pattern = r"(is HidAction.GamepadUpdate ->\n\s*// [^\n]+\n\s*report\(HidDescriptors.REPORT_ID_GAMEPAD, ReportPool.gamepadInto\(action.snapshot\)\))"
replacement = r"""\1
                is HidAction.ReleaseAll -> {
                    report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                    report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
                    report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
                    report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())
                    report(HidDescriptors.REPORT_ID_GAMEPAD, HidReportBuilder.gamepadNeutral())
                }"""

text = re.sub(execute_action_pattern, replacement, text)

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'w') as f:
    f.write(text)
