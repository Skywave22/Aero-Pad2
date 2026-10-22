#!/usr/bin/env bash
# AeroPad — set up (or restore) the release signing secrets.
#
# Why this exists: without a release keystore the Android build falls back to
# the debug key. A debug-signed APK cannot be installed over an installed
# AeroPad release ("App not installed"), and it must never be published. This
# script either reuses the key that signed v3.4.0/v3.5.0, or creates a new one,
# then uploads the four GitHub secrets the CI needs.
#
# Usage (run on your own machine, needs `gh` logged in):
#
#   tools/release-signing.sh                 # restore: use existing keystore
#   tools/release-signing.sh --new           # generate a brand-new key
#   tools/release-signing.sh --keystore path/to/aeropad-release.p12
#
# IMPORTANT: if you still have the keystore that signed v3.4.0, use it. A new
# key means every existing install must be uninstalled first, and anything you
# published before can never be updated in place.
set -euo pipefail

REPO="${REPO:-Skywave22/Aero-Pad2}"
KEYSTORE="${KEYSTORE:-aeropad-release.p12}"
ALIAS="${ALIAS:-AEROPAD}"
MODE="restore"

while [ $# -gt 0 ]; do
  case "$1" in
    --new) MODE="new"; shift ;;
    --keystore) KEYSTORE="$2"; shift 2 ;;
    --alias) ALIAS="$2"; shift 2 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

command -v gh >/dev/null || { echo "error: gh (GitHub CLI) is required" >&2; exit 1; }
command -v keytool >/dev/null || { echo "error: keytool (JDK) is required" >&2; exit 1; }

if [ "$MODE" = "new" ]; then
  if [ -e "$KEYSTORE" ]; then
    echo "error: $KEYSTORE already exists — refusing to overwrite a signing key." >&2
    echo "       Move it away first if you really mean to create a new identity." >&2
    exit 1
  fi
  read -rsp "Choose a keystore password (min 6 chars): " STOREPASS; echo
  read -rsp "Repeat it: " STOREPASS2; echo
  [ "$STOREPASS" = "$STOREPASS2" ] || { echo "error: passwords differ" >&2; exit 1; }
  keytool -genkeypair \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -dname "CN=AeroPad, OU=Mobile, O=Skywave22, L=Bahawalpur, ST=Punjab, C=PK"
  KEYPASS="$STOREPASS"
  echo
  echo "!! BACK THIS FILE UP NOW ($KEYSTORE) and keep the password safe."
  echo "!! Losing it means no future update can be installed over an existing app."
else
  [ -f "$KEYSTORE" ] || { echo "error: $KEYSTORE not found. Use --keystore <path>, or --new to create one." >&2; exit 1; }
  read -rsp "Keystore password for $KEYSTORE: " STOREPASS; echo
  read -rsp "Key password for alias $ALIAS (press Enter if same): " KEYPASS; echo
  [ -n "$KEYPASS" ] || KEYPASS="$STOREPASS"
fi

echo "--- what this key signs with ---"
keytool -list -v -keystore "$KEYSTORE" -storepass "$STOREPASS" -alias "$ALIAS" \
  | grep -E "Alias name|Owner|SHA256:" || true

echo
echo "--- uploading secrets to $REPO ---"
B64=$(base64 -w0 < "$KEYSTORE" 2>/dev/null || base64 < "$KEYSTORE" | tr -d '\n')
printf '%s' "$B64" | gh secret set KEYSTORE_B64 --repo "$REPO"
gh secret set KEYSTORE_PASS --repo "$REPO" --body "$STOREPASS"
gh secret set KEY_ALIAS     --repo "$REPO" --body "$ALIAS"
gh secret set KEY_PASS      --repo "$REPO" --body "$KEYPASS"

echo
echo "Done. Trigger a build:  gh workflow run 'Build AeroPad' --repo $REPO"
echo "The run summary must show '## Signature' with CN=...O=Skywave22..., not CN=Android Debug."
