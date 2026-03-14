#!/usr/bin/env bash
# Generates a self-signed PKCS12 keystore for local HTTPS development.
# Do NOT commit the resulting .p12 file — it is gitignored.
#
# Usage:
#   bash scripts/generate-dev-cert.sh
#   KEYSTORE_PASS=mypassword bash scripts/generate-dev-cert.sh
#
# The keystore is placed at:
#   hlm-backend/src/main/resources/ssl/hlm-dev.p12
#
# To use it, set in your environment or .env:
#   SSL_ENABLED=true
#   SERVER_PORT=8443
#   SSL_KEYSTORE_PATH=classpath:ssl/hlm-dev.p12
#   SSL_KEYSTORE_PASSWORD=changeit   (or $KEYSTORE_PASS)
#   SSL_KEY_ALIAS=hlm-dev

set -euo pipefail

KEYSTORE_DIR="hlm-backend/src/main/resources/ssl"
KEYSTORE_FILE="$KEYSTORE_DIR/hlm-dev.p12"
ALIAS="hlm-dev"
PASS="${KEYSTORE_PASS:-changeit}"

mkdir -p "$KEYSTORE_DIR"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$PASS" \
  -dname "CN=localhost, OU=Dev, O=HLM, L=Casablanca, ST=MA, C=MA" \
  -ext "SAN=DNS:localhost,IP:127.0.0.1" \
  -noprompt

echo ""
echo "✓ Keystore created: $KEYSTORE_FILE"
echo "  Alias  : $ALIAS"
echo "  Pass   : $PASS"
echo ""
echo "Start the backend with TLS:"
echo "  SSL_ENABLED=true SERVER_PORT=8443 SSL_KEYSTORE_PASSWORD=$PASS \\"
echo "    cd hlm-backend && ./mvnw spring-boot:run"
echo ""
echo "Verify:"
echo "  curl -k https://localhost:8443/actuator/health"
