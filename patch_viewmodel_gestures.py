import re

with open('app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt', 'r') as f:
    text = f.read()

funcs = """    
    private var threeFingerAccumX = 0f
    private var threeFingerAccumY = 0f
    
    fun onThreeFingerSwipe(dx: Float, dy: Float) {
        threeFingerAccumX += dx
        threeFingerAccumY += dy
        
        // threshold for swipe
        if (threeFingerAccumY < -150f) {
            // Swipe Up -> Show Task View / Mission Control (Windows: Win+Tab)
            sendAction(HidAction.KeyboardCombo(HidKeys.KEY_LEFT_META, HidKeys.KEY_TAB))
            threeFingerAccumY = 0f
            threeFingerAccumX = 0f
        } else if (threeFingerAccumY > 150f) {
            // Swipe Down -> Show Desktop (Windows: Win+D)
            sendAction(HidAction.KeyboardCombo(HidKeys.KEY_LEFT_META, HidKeys.KEY_D))
            threeFingerAccumY = 0f
            threeFingerAccumX = 0f
        } else if (threeFingerAccumX > 150f) {
            // Swipe Right -> Next app / tab
            sendAction(HidAction.KeyboardCombo(HidKeys.KEY_LEFT_ALT, HidKeys.KEY_TAB))
            threeFingerAccumY = 0f
            threeFingerAccumX = 0f
        } else if (threeFingerAccumX < -150f) {
            // Swipe Left -> Prev app
            sendAction(HidAction.KeyboardCombo(HidKeys.KEY_LEFT_ALT, HidKeys.KEY_LEFTSHIFT, HidKeys.KEY_TAB))
            threeFingerAccumY = 0f
            threeFingerAccumX = 0f
        }
    }
    
    fun onThreeFingerSwipeEnd() {
        threeFingerAccumX = 0f
        threeFingerAccumY = 0f
    }
"""

text = text.replace("    /** Reset motion state when a new gesture starts (prevents smoothing bleed). */", funcs + "\n    /** Reset motion state when a new gesture starts (prevents smoothing bleed). */")

with open('app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt', 'w') as f:
    f.write(text)
