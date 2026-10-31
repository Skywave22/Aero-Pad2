# Release signing — why the v3.6.0 APKs were debug-signed, and how to fix it

## What was actually happening

`v3.4.0` and `v3.5.0` on the Releases page are signed with the real AeroPad key:

```
Issuer: C=PK, ST=Punjab, L=Bahawalpur, O=Skywave22, OU=Mobile, CN=AeroPad
SHA256: E9:65:70:01:16:87:23:18:9F:90:52:9E:83:C1:94:75:A6:09:87:FE:A2:29:DD:95:90:89:06:83:20:22:F3:F7
alias: AEROPAD (PKCS12, valid until 2054)
```

Every `v3.6.0` build since then is signed with the throwaway Android debug key:

```
Subject: CN=Android Debug, O=Android, C=US
SHA256: E7:84:07:C5:3D:53:CD:55:51:53:6F:EA:B1:64:EC:55:D4:B5:EC:EF:99:DB:32:1B:FB:7F:59:FB:CC:36:76:AC
```

### Why

`app/build.gradle.kts` falls back to the debug signing config when no release
keystore is supplied, and the workflow only publishes a GitHub Release when the
`KEYSTORE_B64` secret exists. Those four repository secrets
(`KEYSTORE_B64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`) are no longer present
on the repo — they were when v3.5.0 shipped on 2026-10-20 09:29 UTC, so the
keystore material was removed or the secrets were cleared afterwards.

The result was the worst combination: **no release was published, and the APK
that *was* produced looked like a release but was signed with the debug key** —
so it cannot be installed over an existing AeroPad install (Android refuses a
signature mismatch with "App not installed"), and it is not updateable later.

## What was actually done (Round 13)

The repository-secret API was not writable with the available token, so the key
is **committed to the repo** at `keys/release/` and the build reads it
automatically (priority: environment → Gradle properties → `keys/release/`).
`keys/release/README.md` documents the trade-off and how to move it back into
secrets later.

The new key (this replaces the lost one, and is what 3.6.0 is signed with):

```
Owner:   CN=AeroPad, OU=Mobile, O=Skywave22, L=Bahawalpur, ST=Punjab, C=PK
Alias:   AEROPAD          PKCS#12, RSA 4096, valid until 2054
SHA-256: 57:D0:0E:93:A6:35:83:66:D5:FE:C0:EE:97:A3:7D:C0:7E:64:17:D9:A9:BE:8F:74:4F:A5:1B:18:9E:A4:9C:DB
```

**Because this key is not the one that signed v3.4.0/v3.5.0, uninstall once:**

```bash
adb uninstall com.aeropad.remote
```

Then install `AeroPad-v3.6.0-release.apk`. Every release after this updates in
place normally.

## How to restore the old key instead

```bash
# If you still have the keystore that signed v3.4.0 (aeropad-release.p12/.jks):
tools/release-signing.sh --keystore /path/to/aeropad-release.p12

# If it is gone for good:
tools/release-signing.sh --new
```

The script verifies the key, prints its SHA-256, and uploads all four secrets
with `gh`. Then:

```bash
gh workflow run "Build AeroPad" --repo Skywave22/Aero-Pad2
```

Check the run summary: it must show a **## Signature** block with
`CN=AeroPad, O=Skywave22` — not `CN=Android Debug`. The APK will be attached to
the release as `AeroPad-v3.6.0-release.apk`.

## If the old key is lost

A new key is a new app identity. One-time consequences:

- Uninstall AeroPad from any device that has 3.4.0/3.5.0 before installing 3.6.0
  (`adb uninstall com.aeropad.remote`). Local layouts and settings are lost.
- Anyone who installed an earlier release must do the same.

Back up the new keystore and password somewhere you will still have them in
2028. There is no recovery.

## How the build protects itself now

- **Loud preflight.** A missing secret produces a `::error::` annotation and a
  warning in the job summary instead of a silent fallback.
- **Hard failure.** When the keystore *is* present, the build runs with
  `REQUIRE_RELEASE_SIGNING=1`, which turns the debug fallback into a Gradle
  error rather than a quiet substitution.
- **Proof, not trust.** `apksigner verify --print-certs` reads the certificate
  out of the finished APK, prints it in the summary, and fails the job if a
  build believed to be signed carries `CN=Android Debug` or no signature.
- **Unmistakable file names.** Signed: `AeroPad-v3.6.0-release.apk`.
  Not signed: `AeroPad-v3.6.0-DEBUG-SIGNED.apk`. The manifest also carries
  `<meta-data android:name="com.aeropad.remote.SIGNING" .../>` with
  `release` or `debug`.
- **Publication gated on the certificate check**, so a debug build can never
  reach the Releases page.

---

## Round 17 — the key is back in repository secrets (the committed copy is gone)

From Round 17 the release keystore is **not in the repository**. It lives in
four repository secrets and the workflow refuses to build without them:

| Secret | Value |
| --- | --- |
| `KEYSTORE_B64` | `base64 -w0 aeropad-release.p12` |
| `KEYSTORE_PASS` | store password |
| `KEY_ALIAS` | `AEROPAD` |
| `KEY_PASS` | key password |

The APK is signed with this certificate, and **CI fails the run** if a build
comes out with any other one:

```
Owner: CN=AeroPad, OU=Mobile, O=Skywave22, L=Bahawalpur, ST=Punjab, C=PK
SHA-256: 57:D0:0E:93:A6:35:83:66:D5:FE:C0:EE:97:A3:7D:C0:7E:64:17:D9:A9:BE:8F:74:4F:A5:1B:18:9E:A4:9C:DB
Valid until 2054, RSA 4096, alias AEROPAD
```

Note this is *not* the v3.4.0/v3.5.0 certificate above (`E9:65:…`). That key was
lost, so it was regenerated in Round 13; every build from Round 13 onwards is
signed with `57:D0:…`, which is why one uninstall was required when moving from
v3.5.0. From v3.6.0 onward the certificate is stable and pinned in CI.

**Rules that now hold:**

1. No keystore, no `.jks`, no `.p12` and no `keystore.properties` is tracked by
   git (`.gitignore` blocks all four, and nothing under `/keys/` is whitelisted).
2. `./gradlew assembleRelease` reads `RELEASE_KEYSTORE_FILE`,
   `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` from
   the environment (or from Gradle properties for a local build).
3. With `REQUIRE_RELEASE_SIGNING=1` — which CI always sets — a missing key is a
   hard build failure, not a debug-signed APK.
4. After packaging, CI reads the certificate out of the APK and compares it with
   `57:D0:0E:…:9C:DB`. Anything else fails the job before publication.

**Keeping a backup:** without this keystore you cannot ship an update to an
existing install, ever. Keep an offline copy (password manager + a file) of
`aeropad-release.p12` and its two passwords.
