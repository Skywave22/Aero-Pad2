import re

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'r') as f:
    text = f.read()

# Look for the duplicated code. The text is very mangled.
# I'll just write a script to find `if (app.secureScreen) {` and clean it up.

def clean_file(content):
    # This is a bit risky, let's just replace the whole launched effect block for `secureScreen`
    
    # We will locate `androidx.compose.runtime.LaunchedEffect(app.secureScreen)`
    match = re.search(r'(androidx\.compose\.runtime\.LaunchedEffect\(app\.secureScreen\).*?)androidx\.compose\.runtime\.LaunchedEffect\(app\.fullscreenMode\)', content, re.DOTALL)
    if not match:
        return content # couldn't find
    
    good_block = """androidx.compose.runtime.LaunchedEffect(app.secureScreen) {
                if (app.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            
            """
    
    return content.replace(match.group(1), good_block)

text = clean_file(text)

# Also fix fpsOverlay duplication.
# I will find the first `// V2 PART A — real-time FPS overlay` and remove everything after the first Box till the next legitimate statement.

import re
text = re.sub(r'(\s*// V2 PART A — real-time FPS overlay.*?\}\s*\n\s*\})[\s\S]+?// V2 PART A — real-time FPS overlay.*?\}\s*\n\s*\}', r'\1', text)

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'w') as f:
    f.write(text)
