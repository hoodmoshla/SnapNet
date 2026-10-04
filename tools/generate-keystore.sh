#!/usr/bin/env bash
#
# Creates the permanent SnapNet release keystore.
#
# The keystore is created ONCE and then never regenerated: Android refuses to install an APK signed
# with a different key over an existing installation, so changing it would strand every existing
# user on the old build. Back the file and its password up somewhere durable.
#
# The output deliberately goes OUTSIDE the repository. The base64 blob it prints is the value for
# the SIGNING_KEY repository secret.
#
# Usage: tools/generate-keystore.sh [output-dir]
set -euo pipefail

OUT="${1:-$HOME/snapnet-signing}"
ALIAS="snapnet"
VALIDITY_DAYS=10950  # ~30 years

mkdir -p "$OUT"
chmod 700 "$OUT"
cd "$OUT"

if [ -f snapnet-release.jks ]; then
  echo "snapnet-release.jks already exists in $OUT - refusing to overwrite."
  echo "Regenerating it would break updates for every installed user."
  exit 1
fi

PASS="$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)"

keytool -genkeypair -v \
  -keystore snapnet-release.jks \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity "$VALIDITY_DAYS" \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=SnapNet, OU=Release, O=SnapNet, C=US"

base64 -w0 snapnet-release.jks > snapnet-release.jks.b64
echo "$PASS" > .pw

chmod 600 snapnet-release.jks snapnet-release.jks.b64 .pw

echo
echo "Keystore : $OUT/snapnet-release.jks"
echo "Alias    : $ALIAS"
echo "Password : $PASS"
echo
echo "Signer certificate SHA-256:"
keytool -list -v -keystore snapnet-release.jks -storepass "$PASS" -alias "$ALIAS" \
  | awk '/SHA256:/{gsub(/^[ \t]+/,""); print "  " $0; exit}'
echo
echo "Create these GitHub repository secrets:"
echo "  SIGNING_KEY        <- contents of snapnet-release.jks.b64"
echo "  KEY_STORE_PASSWORD <- $PASS"
echo "  ALIAS              <- $ALIAS"
echo "  KEY_PASSWORD       <- $PASS"
