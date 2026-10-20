#!/usr/bin/env bash
# Fails if the APK requests any permission that is not in config/allowed-permissions.txt.
set -euo pipefail

apk="$1"
allowlist="config/allowed-permissions.txt"
aapt2="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/aapt2"

requested="$("$aapt2" dump permissions "$apk" \
  | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort -u)"
allowed="$(grep -v '^\s*#' "$allowlist" | sed '/^\s*$/d' | sort -u)"

echo "Permissions requested by the APK:"
echo "${requested:-  (none)}" | sed 's/^/  /'

unexpected="$(comm -23 <(echo "$requested") <(echo "$allowed") | sed '/^$/d')"
if [[ -n "$unexpected" ]]; then
  echo "::error::The APK requests permissions that are not on the allowlist:"
  echo "$unexpected" | sed 's/^/  /'
  echo "If this is intended, add them to $allowlist with a comment explaining why."
  exit 1
fi
echo "Permission audit passed."
