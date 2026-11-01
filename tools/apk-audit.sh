#!/usr/bin/env bash
# AeroPad — static audit of a built APK. No device, no emulator, no API key.
#
#   tools/apk-audit.sh app/build/outputs/apk/release/app-release.apk [report.md]
#
# Prints a Markdown report and exits non-zero if a FAIL-level check trips, so it
# can gate CI. Findings are facts about the binary (permissions, exported
# components, manifest flags, embedded strings), not guesses about the source.
set -uo pipefail

EXPECT_RELEASE=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --expect-release) EXPECT_RELEASE=1 ;;
    *) ARGS+=("$a") ;;
  esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"
APK="${1:-}"
OUT="${2:-}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "usage: $0 <path-to.apk> [report.md]" >&2
  exit 2
fi

# ---- locate build-tools -----------------------------------------------------
find_bt() {
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    [ -n "$d" ] && [ -d "$d/build-tools" ] && ls -1d "$d"/build-tools/* 2>/dev/null | sort -V | tail -1 && return 0
  done
  return 1
}
BT=$(find_bt)
AAPT2=""; AAPT=""; APKSIGNER=""
if [ -n "$BT" ]; then
  [ -x "$BT/aapt2" ] && AAPT2="$BT/aapt2"
  [ -x "$BT/aapt" ] && AAPT="$BT/aapt"
  [ -x "$BT/apksigner" ] && APKSIGNER="$BT/apksigner"
fi
if [ -z "$AAPT2" ] && [ -z "$AAPT" ]; then
  echo "error: aapt2/aapt not found. Install Android build-tools and set ANDROID_HOME." >&2
  exit 2
fi
dump_badging() { if [ -n "$AAPT2" ]; then "$AAPT2" dump badging "$APK"; else "$AAPT" dump badging "$APK"; fi; }
dump_xmltree() { if [ -n "$AAPT2" ]; then "$AAPT2" dump xmltree --file "$1" "$APK"; else "$AAPT" dump xmltree "$APK" "$1"; fi; }

FAILS=0; WARNS=0
say()  { printf '%s\n' "$*" >> "$TMP"; }
fail() { FAILS=$((FAILS+1)); say "- :x: **FAIL** — $*"; }
warn() { WARNS=$((WARNS+1)); say "- :warning: **WARN** — $*"; }
pass() { say "- :white_check_mark: $*"; }

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

BADGING=$(dump_badging 2>/dev/null)
PKG=$(printf '%s\n' "$BADGING"   | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
VCODE=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")
VNAME=$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
MINSDK=$(printf '%s\n' "$BADGING"| sed -n "s/^minSdkVersion:'\([^']*\)'.*/\1/p")
TGTSDK=$(printf '%s\n' "$BADGING"| sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p")
LAUNCH=$(printf '%s\n' "$BADGING"| sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p")

say "# APK audit — $(basename "$APK")"
say ""
say "Generated $(date -u '+%Y-%m-%d %H:%M UTC') by \`tools/apk-audit.sh\` (no network, no API key)."
say ""
say "| | |"
say "|---|---|"
say "| Package | \`$PKG\` |"
say "| Version | $VNAME ($VCODE) |"
say "| minSdk / targetSdk | $MINSDK / $TGTSDK |"
say "| Launchable activity | \`$LAUNCH\` |"
say "| Size | $(( $(stat -c%s "$APK" 2>/dev/null || stat -f%z "$APK") / 1024 )) KiB |"
say ""

# ---- signature --------------------------------------------------------------
say "## Signature"
say ""
if [ -n "$APKSIGNER" ]; then
  CERT=$("$APKSIGNER" verify --print-certs "$APK" 2>&1)
  DN=$(printf '%s\n' "$CERT" | sed -n 's/^Signer #1 certificate DN: //p' | head -1)
  SHA=$(printf '%s\n' "$CERT" | sed -n 's/.*SHA-256 digest: //p' | head -1)
  say '```'
  say "DN:      ${DN:-none}"
  say "SHA-256: ${SHA:-none}"
  say '```'
  say ""
  case "$DN" in
    *"Android Debug"*)
      if [ "$EXPECT_RELEASE" = "1" ]; then
        fail "signed with the **Android debug key** — this must be a release build"
      else
        warn "signed with the **Android debug key** (expected for a debug build)"
      fi;;
    "")                fail "APK is not signed";;
    *)                 pass "signed: ${DN}";;
  esac
else
  warn "apksigner not found; signature not checked"
fi

# ---- permissions ------------------------------------------------------------
say ""
say "## Permissions"
say ""
PERMS=$(printf '%s\n' "$BADGING" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")
if [ -z "$PERMS" ]; then
  pass "none declared"
else
  say '```'
  printf '%s\n' "$PERMS" | sed 's/^/  /' >> "$TMP"
  say '```'
  if printf '%s\n' "$PERMS" | grep -q "android.permission.INTERNET"; then
    warn "declares **INTERNET** — this app is documented as offline-only; justify it or remove it"
  else
    pass "no INTERNET permission (the app cannot phone home)"
  fi
  if printf '%s\n' "$PERMS" | grep -q "REQUEST_INSTALL_PACKAGES"; then
    fail "REQUEST_INSTALL_PACKAGES lets this app install other APKs"
  fi
fi
say ""

# ---- manifest flags and exported components ---------------------------------
say "## Manifest hardening"
say ""
TREE=$(dump_xmltree AndroidManifest.xml 2>/dev/null)
if [ -z "$TREE" ]; then
  warn "could not read AndroidManifest.xml"
else
  # aapt2 prints raw values (=true / =false), aapt prints typed ones
  # (=(type 0x12)0xffffffff). Accept both.
  flag_is_true()  { printf '%s\n' "$1" | grep -qE "=$2|\(type 0x12\)0xffffffff|\)=true"; }
  flag_is_false() { printf '%s\n' "$1" | grep -qE "=$2|\(type 0x12\)0x0|\)=false"; }

  APP_LINE=$(printf '%s\n' "$TREE" | grep -m1 'allowBackup')

  if printf '%s\n' "$TREE" | grep -qE 'debuggable\(0x0101000f\)=(true|\(type 0x12\)0xffffffff)'; then
    if [ "$EXPECT_RELEASE" = "1" ]; then
      fail "**debuggable=\"true\"** — a release build must not be debuggable"
    else
      pass "debuggable=\"true\" (this is a debug build, as expected)"
    fi
  else
    pass "not debuggable"
  fi

  if flag_is_false "$APP_LINE" false; then
    pass "allowBackup is disabled"
  elif flag_is_true "$APP_LINE" true; then
    warn "allowBackup is enabled — app data can be extracted with adb backup on older Androids"
  else
    warn "allowBackup not set explicitly (defaults to true)"
  fi

  if printf '%s\n' "$TREE" | grep -qE 'usesCleartextTraffic\(0x010104ec\)=(true|\(type 0x12\)0xffffffff)'; then
    warn "usesCleartextTraffic=true — plain HTTP is allowed"
  fi

  # Walk the tree: for every component element, record its name, whether it is
  # exported, and any permission guard.
  COMPONENTS=$(printf '%s\n' "$TREE" | awk '
    function flush() {
      if (curtype != "" && isexp == "true" && compname != "") {
        printf "  %-9s %-58s %s\n", curtype, compname, (compperm == "" ? "NO PERMISSION GUARD" : "perm=" compperm);
      }
      curtype = ""; compname = ""; isexp = ""; compperm = "";
    }
    /^ *E: (activity|activity-alias|service|receiver|provider)/ {
      flush();
      l = $0; sub(/^ *E: /, "", l); sub(/ .*/, "", l); curtype = l; next
    }
    /^ *E: / { flush(); next }
    /android:name\(0x01010003\)=/ && compname == "" {
      v = $0; sub(/.*android:name\(0x01010003\)=/, "", v); sub(/ \(Raw.*/, "", v); gsub(/"/, "", v);
      compname = v; next
    }
    /android:exported\(0x01010010\)=/ {
      isexp = ($0 ~ /=(true|\(type 0x12\)0xffffffff)/) ? "true" : "false"; next
    }
    /android:permission\(0x01010006\)=/ && compperm == "" {
      v = $0; sub(/.*android:permission\(0x01010006\)=/, "", v); sub(/ \(Raw.*/, "", v); gsub(/"/, "", v);
      compperm = v; next
    }
    END { flush() }
  ')

  EXPORTED=$(printf '%s\n' "$COMPONENTS" | grep -c 'NO PERMISSION GUARD')
  TOTAL_EXPORTED=$(printf '%s\n' "$COMPONENTS" | grep -c .)

  say ""
  say "Exported components: **$TOTAL_EXPORTED** (of which **$EXPORTED** have no permission guard)"
  say ""
  if [ "$TOTAL_EXPORTED" -gt 0 ]; then
    say '```'
    printf '%s\n' "$COMPONENTS" >> "$TMP"
    say '```'
    say ""
    say "Exported means any app on the device can reach it. That is correct for the launcher activity and often for a deep-link entry point; for anything else it is attack surface that needs its own authorisation check."
    if [ "$EXPORTED" -gt 0 ]; then
      warn "$EXPORTED exported component(s) rely on their own internal checks rather than an android:permission"
    fi
  else
    pass "no exported components beyond the launcher path"
  fi
fi

# ---- contents ---------------------------------------------------------------
say ""
say "## Contents"
say ""
LIST=$(unzip -l "$APK" 2>/dev/null)
DEXN=$(printf '%s\n' "$LIST" | grep -cE '\.dex$')
LIBS=$(printf '%s\n' "$LIST" | grep -oE 'lib/[^/]+/' | sort -u | tr -d ' ' | tr '\n' ' ')
say "- dex files: **$DEXN**$( [ "$DEXN" -gt 1 ] && echo " (multidex)" )"
say "- native ABIs: ${LIBS:-none}"
say "- resources: $(printf '%s\n' "$LIST" | grep -cE 'res/')"
if printf '%s\n' "$LIST" | grep -qE 'assets/.*\.(pem|p12|jks|keystore)$'; then
  fail "key material shipped inside the APK"
fi
if printf '%s\n' "$LIST" | grep -qE 'META-INF/.*\.(RSA|DSA|EC)$'; then
  say "- v1 (JAR) signature entries: present"
fi

# ---- embedded strings -------------------------------------------------------
say ""
say "## Embedded strings (from classes.dex)"
say ""
TMPD=$(mktemp -d)
trap 'rm -rf "$TMPD"; rm -f "$TMP"' EXIT
unzip -o -q "$APK" 'classes*.dex' -d "$TMPD" 2>/dev/null

scan() { strings -a "$TMPD"/classes*.dex 2>/dev/null | grep -Ei "$1" | sort -u | head -"${2:-5}"; }

HITS=0
for pat in 'api[_-]?key[[:space:]]*[:=]' 'secret[[:space:]]*[:=]' 'BEGIN [A-Z ]*PRIVATE KEY' 'AIza[0-9A-Za-z_-]{30,}' 'ghp_[0-9A-Za-z]{20,}' 'sk-[0-9A-Za-z]{20,}'; do
  M=$(scan "$pat" 2>/dev/null)
  if [ -n "$M" ]; then
    fail "possible credential in the binary (pattern: \`$pat\`)"
    say '```'
    printf '%s\n' "$M" | sed 's/^/  /' >> "$TMP"
    say '```'
    HITS=$((HITS+1))
  fi
done
[ "$HITS" -eq 0 ] && pass "no API keys, tokens or private keys found in the DEX strings"

URLS=$(strings -a "$TMPD"/classes*.dex 2>/dev/null | grep -oE 'https?://[a-zA-Z0-9./_-]+' | sort -u | head -10)
say ""
if [ -n "$URLS" ]; then
  say "URLs referenced in code (context matters — SDK endpoints are normal):"
  say '```'
  printf '%s\n' "$URLS" | sed 's/^/  /' >> "$TMP"
  say '```'
else
  pass "no URLs compiled into the app"
fi

# ---- verdict ----------------------------------------------------------------
say ""
say "## Verdict"
say ""
if [ "$FAILS" -gt 0 ]; then
  say "**:x: $FAILS failure(s), $WARNS warning(s).**"
else
  say "**:white_check_mark: No failures.** $WARNS warning(s) to review."
fi
say ""

if [ -n "$OUT" ]; then
  cp "$TMP" "$OUT"
  echo "report written to $OUT" >&2
else
  cat "$TMP"
fi

[ "$FAILS" -eq 0 ]
