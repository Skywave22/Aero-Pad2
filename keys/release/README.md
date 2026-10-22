# AeroPad release signing key

`aeropad-release.p12` — alias `AEROPAD`, PKCS#12, RSA 4096, valid until 2054.

```
Owner: CN=AeroPad, OU=Mobile, O=Skywave22, L=Bahawalpur, ST=Punjab, C=PK
SHA-256: 57:D0:0E:93:A6:35:83:66:D5:FE:C0:EE:97:A3:7D:C0:7E:64:17:D9:A9:BE:8F:74:4F:A5:1B:18:9E:A4:9C:DB
```

Passwords are in `keystore.properties` next to it. The build reads these
automatically when no CI secrets or Gradle properties are present, so a
release build from this repo is signed — it is no longer debug-signed.

## Read this before you change anything

**This repository is public.** Anyone can read this key and sign an APK that
claims to be AeroPad. That is the price of having working signed builds without
repository-secret write access. Only install AeroPad from this repo's Releases
page.

## This key is newer than v3.4.0 / v3.5.0

Those releases were signed with an earlier key whose secrets were removed from
the repo after 2026-10-20. Because Android requires a matching signature for
in-place updates, a device with 3.4.0/3.5.0 installed must uninstall AeroPad
once before installing 3.6.0:

```bash
adb uninstall com.aeropad.remote
```

Everything after 3.6.0 updates normally.

## Moving it back into secrets (recommended when you can)

```bash
tools/release-signing.sh --keystore keys/release/aeropad-release.p12
```

That uploads `KEYSTORE_B64`, `KEYSTORE_PASS`, `KEY_ALIAS` and `KEY_PASS` with
`gh`, and the build prefers them over this folder. Then:

```bash
git rm -r --cached keys/release
rm -rf keys/release
```

and commit. The workflow's preflight will confirm the secrets are in place.

## Back it up

Losing this file means no future AeroPad build can update an existing install.
It is already in git history, so treat the repo as the backup — and if the repo
is ever deleted, the signing identity goes with it.
