import re

with open('app/src/main/java/com/aeropad/remote/viewmodel/ConnectionHealthViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace("_snapshot.value = engine.health.snapshot()", "_snapshot.value = engine.getHealthSnapshot()")

with open('app/src/main/java/com/aeropad/remote/viewmodel/ConnectionHealthViewModel.kt', 'w') as f:
    f.write(text)
