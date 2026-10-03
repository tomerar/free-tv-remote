#!/usr/bin/env bash
# Prepares the APK signing key for the release workflow and exports the SIGNING_* variables via $GITHUB_ENV.
#
#   prepare-signing.sh publish   Stable project key from repository secrets. Fails early and clearly if
#                                anything is missing. Never falls back to a generated key.
#   prepare-signing.sh dev       Development build only: a one-off key. Such an APK installs fine but can
#                                never be updated by (or update) any other build. Must not be published.
set -euo pipefail

mode="${1:?usage: prepare-signing.sh publish|dev}"
: "${GITHUB_ENV:?GITHUB_ENV must point to the workflow environment file}"
workdir="${RUNNER_TEMP:-$(mktemp -d)}"
keystore="$workdir/release.jks"

export_env() { printf '%s=%s\n' "$1" "$2" >> "$GITHUB_ENV"; }

case "$mode" in
  publish)
    missing=()
    for name in SIGNING_KEY_BASE64 SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
      [ -n "${!name:-}" ] || missing+=("$name")
    done
    if [ "${#missing[@]}" -gt 0 ]; then
      echo "::error title=Release signing is not configured::Published releases must be signed with the stable project key, and these repository secrets are missing or empty: ${missing[*]}. Nothing was built or published. See docs/RELEASING.md."
      exit 1
    fi
    if ! printf '%s' "$SIGNING_KEY_BASE64" | base64 -d > "$keystore" 2>/dev/null || [ ! -s "$keystore" ]; then
      echo "::error title=Release signing key is unreadable::SIGNING_KEY_BASE64 is not valid base64. See docs/RELEASING.md."
      exit 1
    fi
    if ! keytool -list -keystore "$keystore" -storepass "$SIGNING_STORE_PASSWORD" -alias "$SIGNING_KEY_ALIAS" > /dev/null 2>&1; then
      echo "::error title=Release signing key does not open::The keystore could not be opened with the given store password and alias. See docs/RELEASING.md."
      exit 1
    fi
    export_env SIGNING_STORE_FILE "$keystore"
    export_env SIGNING_STORE_PASSWORD "$SIGNING_STORE_PASSWORD"
    export_env SIGNING_KEY_ALIAS "$SIGNING_KEY_ALIAS"
    export_env SIGNING_KEY_PASSWORD "$SIGNING_KEY_PASSWORD"
    export_env REQUIRE_RELEASE_SIGNING true
    export_env SIGNING_KIND "stable project key"
    ;;
  dev)
    pass="$(openssl rand -hex 16)"
    keytool -genkeypair -keystore "$keystore" -storepass "$pass" -keypass "$pass" \
      -alias release -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Free TV Remote DEVELOPMENT build" > /dev/null 2>&1
    export_env SIGNING_STORE_FILE "$keystore"
    export_env SIGNING_STORE_PASSWORD "$pass"
    export_env SIGNING_KEY_ALIAS release
    export_env SIGNING_KEY_PASSWORD "$pass"
    export_env REQUIRE_RELEASE_SIGNING true
    export_env SIGNING_KIND "one-off development key (not update-compatible)"
    ;;
  *)
    echo "unknown mode: $mode" >&2
    exit 2
    ;;
esac
