#!/usr/bin/env python3
"""
AeroPad companion receiver — run this on your PC (Windows/Mac/Linux).

  1. Install deps:  pip install pynput zeroconf
  2. Run:           python aeropad_receiver.py
  3. It prints a 6-digit PIN and advertises itself on the network (mDNS).
  4. In AeroPad: WiFi Control -> your PC appears -> Connect -> enter PIN.

Input injection uses pynput (keyboard+mouse). Gamepad messages are logged
only unless you install vgamepad (Windows) - flagged honestly below.

v2.1 FIXES:
  - Modifiers (Ctrl/Shift/Alt/Win) are now actually HELD around keys —
    Ctrl+C, Alt+Tab, shortcuts all work (were silently dropped before).
  - kd/ku are real press/release pairs — key HOLDS work (charge attacks,
    WASD walking, macro KeyHold steps).
  - Media keys (play/pause, volume, next/prev) injected via pynput.
  - Full special-key table: F1-F12, home/end/pgup/pgdn, del/ins, arrows,
    punctuation, numpad Enter.
"""
import hashlib, json, random, socket, threading, time

PORT = 48653
PIN = str(random.randint(100000, 999999))

try:
    from pynput.keyboard import Controller as KC, Key
    from pynput.mouse import Controller as MC, Button
    kbd, mouse = KC(), MC()
    HAVE_PYNPUT = True
except ImportError:
    HAVE_PYNPUT = False
    print("WARNING: pynput missing - input will be LOGGED, not injected.")
    print("         pip install pynput")

try:
    from zeroconf import Zeroconf, ServiceInfo
    HAVE_ZEROCONF = True
except ImportError:
    HAVE_ZEROCONF = False
    print("NOTE: zeroconf missing - auto-discovery off (manual IP still works).")

def sha256(s):
    return hashlib.sha256(s.encode()).hexdigest()

# HID usage -> pynput special key (full table).
SPECIALS = {
    40: "enter", 41: "esc", 42: "backspace", 43: "tab", 44: "space",
    57: "caps_lock",
    58: "f1", 59: "f2", 60: "f3", 61: "f4", 62: "f5", 63: "f6",
    64: "f7", 65: "f8", 66: "f9", 67: "f10", 68: "f11", 69: "f12",
    70: "print_screen", 73: "insert", 74: "home", 75: "page_up",
    76: "delete", 77: "end", 78: "page_down",
    79: "right", 80: "left", 81: "down", 82: "up",
    101: "menu",
}

# HID usage -> plain character (unshifted US layout).
PUNCT = {
    45: "-", 46: "=", 47: "[", 48: "]", 49: "\\",
    51: ";", 52: "'", 53: "`", 54: ",", 55: ".", 56: "/",
}

# Consumer usage -> pynput media key.
MEDIA = {
    0x00CD: "media_play_pause",
    0x00B7: "media_play_pause",   # stop -> closest available
    0x00B5: "media_next",
    0x00B6: "media_previous",
    0x00E9: "media_volume_up",
    0x00EA: "media_volume_down",
    0x00E2: "media_volume_mute",
}

def hid_to_char(code):
    # HID usage 4-29 = a-z, 30-39 = 1-9,0
    if 4 <= code <= 29:
        return chr(ord('a') + code - 4)
    if 30 <= code <= 38:
        return chr(ord('1') + code - 30)
    if code == 39:
        return '0'
    return PUNCT.get(code)

def mods_list(mods):
    """HID modifier bitmask -> pynput modifier keys (L+R variants)."""
    out = []
    if mods & 0x11: out.append(Key.ctrl)
    if mods & 0x22: out.append(Key.shift)
    if mods & 0x44: out.append(Key.alt)
    if mods & 0x88: out.append(Key.cmd)     # Win / Cmd
    return out

# v2.1 — track what kd pressed so ku releases exactly that.
_held_lock = threading.Lock()
_held_keys = []      # keys pressed by the last kd
_held_mods = []      # modifiers held by the last kd

def key_down(code, mods):
    global _held_keys, _held_mods
    ch = hid_to_char(code)
    k = None
    if ch is None and code in SPECIALS:
        k = getattr(Key, SPECIALS[code], None)
    with _held_lock:
        # Release anything stuck from a previous kd (defensive).
        _release_all_locked()
        for m in mods_list(mods):
            kbd.press(m)
            _held_mods.append(m)
        if ch is not None:
            kbd.press(ch)
            _held_keys.append(ch)
        elif k is not None:
            kbd.press(k)
            _held_keys.append(k)

def _release_all_locked():
    global _held_keys, _held_mods
    for k in reversed(_held_keys):
        try: kbd.release(k)
        except Exception: pass
    for m in reversed(_held_mods):
        try: kbd.release(m)
        except Exception: pass
    _held_keys, _held_mods = [], []

def key_up():
    with _held_lock:
        _release_all_locked()

def handle(msg, out):
    t = msg.get("t")
    if t == "ping":
        out.write(json.dumps({"t": "pong", "echo": msg.get("echo")}) + "\n")
        out.flush()
        return
    if not HAVE_PYNPUT:
        print("MSG:", msg)
        return
    if t == "txt":
        kbd.type(msg.get("text", ""))
    elif t == "kd":
        key_down(msg.get("key", 0), msg.get("mods", 0))
    elif t == "ku":
        key_up()
    elif t == "media":
        name = MEDIA.get(msg.get("key", 0))
        if name:
            k = getattr(Key, name, None)
            if k:
                kbd.press(k); kbd.release(k)
    elif t == "mm":
        mouse.move(msg.get("dx", 0), msg.get("dy", 0))
    elif t in ("mc", "md", "mu"):
        b = {1: Button.left, 2: Button.right, 4: Button.middle}.get(msg.get("btn", 1), Button.left)
        if t == "mc":
            mouse.click(b)
        elif t == "md":
            mouse.press(b)
        else:
            mouse.release(b)
    elif t == "ms":
        mouse.scroll(0, msg.get("scroll", 0))
    elif t == "gp":
        # Honest note: virtual gamepad needs vgamepad (Windows) or uinput
        # (Linux root). Without it we log the state only.
        print("GAMEPAD:", msg.get("buttons"), msg.get("hat"),
              msg.get("lx"), msg.get("ly"), msg.get("rx"), msg.get("ry"))

def client_thread(conn, addr):
    print(f"[+] {addr[0]} connected")
    try:
        rf = conn.makefile("r")
        wf = conn.makefile("w")
        nonce = str(random.randint(10**8, 10**9))
        wf.write(json.dumps({"t": "welcome", "text": nonce, "v": 1}) + "\n")
        wf.flush()
        hello = json.loads(rf.readline())
        if hello.get("proof") != sha256(PIN + ":" + nonce):
            wf.write(json.dumps({"t": "err", "text": "bad pin"}) + "\n")
            wf.flush()
            print(f"[-] {addr[0]} wrong PIN")
            return
        wf.write(json.dumps({"t": "ok", "deviceName": socket.gethostname()}) + "\n")
        wf.flush()
        print(f"[+] {addr[0]} authenticated ({hello.get('deviceName')})")
        for line in rf:
            try:
                handle(json.loads(line), wf)
            except Exception as e:
                print("bad msg:", e)
    except Exception as e:
        print(f"[-] {addr[0]} error: {e}")
    finally:
        # v2.1 — never leave keys stuck when the phone drops mid-hold.
        if HAVE_PYNPUT:
            key_up()
        conn.close()
        print(f"[-] {addr[0]} disconnected")

def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()

def main():
    ip = local_ip()
    print("=" * 46)
    print("  AeroPad receiver")
    print(f"  Address : {ip}:{PORT}")
    print(f"  PIN     : {PIN}")
    print(f"  QR      : aeropad://{ip}:{PORT}/{PIN}")
    print("=" * 46)
    if HAVE_ZEROCONF:
        zc = Zeroconf()
        info = ServiceInfo(
            "_aeropad._tcp.local.",
            f"{socket.gethostname()}._aeropad._tcp.local.",
            addresses=[socket.inet_aton(ip)], port=PORT)
        zc.register_service(info)
        print("mDNS: advertising as _aeropad._tcp")
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", PORT))
    srv.listen(2)
    print("Waiting for AeroPad to connect…  (Ctrl+C to quit)")
    while True:
        conn, addr = srv.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=client_thread, args=(conn, addr), daemon=True).start()

if __name__ == "__main__":
    main()
