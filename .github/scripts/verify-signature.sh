#!/usr/bin/env bash
# Verifies an APK's signature and, for published releases, its signing identity.
#
#   verify-signature.sh <apk> publish|dev
#
# Prints the signer certificate fingerprint (public information) and exports CERT_SHA256 via $GITHUB_ENV.
# In publish mode it rejects the one-off keys of earlier CI builds and, when RELEASE_CERT_SHA256 is set
# (repository variable), requires the signer to match it exactly.
set -euo pipefail

apk="${1:?usage: verify-signature.sh <apk> publish|dev}"
mode="${2:?usage: verify-signature.sh <apk> publish|dev}"
apksigner="${APKSIGNER:-$(ls -d "${ANDROID_HOME:?}"/build-tools/*/apksigner | tail -1)}"

"$apksigner" verify --verbose "$apk" > /dev/null
certs="$("$apksigner" verify --print-certs "$apk")"
subject="$(printf '%s\n' "$certs" | sed -n 's/^Signer #1 certificate DN: //p' | head -1)"
sha="$(printf '%s\n' "$certs" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr -d ': ' | tr 'A-F' 'a-f')"
[ -n "$sha" ] || { echo "::error::could not read the signer certificate"; exit 1; }
echo "Signer: $subject"
echo "Signer certificate SHA-256: $sha"

if [ "$mode" = publish ]; then
  case "$subject" in
    *"CI build"*|*"DEVELOPMENT build"*|*"local build"*)
      echo "::error title=Not a project signing identity::The APK is signed with a one-off key ($subject). Published releases need the stable project key."
      exit 1
      ;;
  esac
  expected="$(printf '%s' "${RELEASE_CERT_SHA256:-}" | tr -d ': ' | tr 'A-F' 'a-f')"
  if [ -n "$expected" ] && [ "$expected" != "$sha" ]; then
    echo "::error title=Unexpected signing identity::The signer certificate SHA-256 is $sha but the repository variable RELEASE_CERT_SHA256 expects $expected. Users could not update from earlier releases."
    exit 1
  fi
  [ -n "$expected" ] || echo "::warning title=Signer not pinned::Set the repository variable RELEASE_CERT_SHA256 to $sha so future releases are checked against it (see docs/RELEASING.md)."
fi

if [ -n "${GITHUB_ENV:-}" ]; then printf 'CERT_SHA256=%s\n' "$sha" >> "$GITHUB_ENV"; fi
