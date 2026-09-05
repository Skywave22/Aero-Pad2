import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/mouse/MouseScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("val isConnected by viewModel.isConnected.collectAsState()", "val isConnected by viewModel.isConnected.collectAsState()\n    val mouseSettings by viewModel.mouseSettings.collectAsState()")

with open('app/src/main/java/com/aeropad/remote/ui/screens/mouse/MouseScreen.kt', 'w') as f:
    f.write(text)
