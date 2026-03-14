# HTTPS / TLS Configuration Guide

This guide covers every step required to run the HLM SaaS Platform over HTTPS,
from local development with a self-signed certificate through production deployment
behind Nginx with a Let's Encrypt certificate.

## 1. Overview

Two deployment modes are supported:

**Mode A — Embedded Tomcat TLS** (dev / staging):
Spring Boot's embedded Tomcat handles TLS directly. A PKCS12 keystore is loaded
from the classpath or a filesystem path. All traffic is encrypted end-to-end from
the client browser to the Java process. Use this mode when deploying a single binary
without a reverse proxy.

**Mode B — Nginx reverse proxy TLS termination** (production):
Nginx terminates TLS on port 443. Spring Boot listens on plain HTTP at
`127.0.0.1:8080`, never exposed to the internet. This is the recommended production
architecture: Nginx handles certificate renewal, cipher negotiation, and security
headers, while the application can be deployed and restarted without TLS key material.

**Why TLS matters for this application:**
Every request to this platform carries either a JWT access token (in the `Authorization`
header) or a one-time magic-link token (in a URL query parameter). Without encryption,
any network observer between the client and server can steal these tokens. TLS prevents
this. All environments that handle real user data must run over HTTPS.

---

## 2. Development (self-signed certificate — Mode A)

### Prerequisites
Java 21 (includes `keytool`). No other tools needed.

### Step 1 — Generate the certificate

```bash
bash scripts/generate-dev-cert.sh
```

This creates `hlm-backend/src/main/resources/ssl/hlm-dev.p12` (PKCS12, self-signed,
valid 10 years, CN=localhost, SAN=DNS:localhost,IP:127.0.0.1). The file is gitignored;
never commit it.

### Step 2 — Set environment variables

```bash
export SSL_ENABLED=true
export SERVER_PORT=8443
export SSL_KEYSTORE_PATH=classpath:ssl/hlm-dev.p12
export SSL_KEYSTORE_PASSWORD=changeit
export SSL_KEY_ALIAS=hlm-dev
export HTTP_REDIRECT_PORT=8080   # plain-HTTP connector that redirects to :8443
```

Or add them to `.env` and source it:

```bash
set -a && source .env && set +a
```

### Step 3 — Start the backend

```bash
cd hlm-backend && ./mvnw spring-boot:run
```

Tomcat starts on port 8443 (HTTPS) plus an additional HTTP connector on port 8080
that issues a 302 redirect to `https://localhost:8443`.

### Step 4 — Verify

```bash
curl -k https://localhost:8443/actuator/health
# Expected: {"status":"UP"}
```

The `-k` flag tells curl to accept the self-signed certificate. In a browser, you will
see a "Not Secure" warning; click through it for local development.

### Angular dev proxy

When the backend is running with TLS, update the proxy target in `hlm-frontend/proxy.conf.json`:
The file already contains `"secure": false` for all proxy entries, which instructs the
Angular dev proxy to accept self-signed certificates. The `target` remains
`http://localhost:8080` if you rely on the HTTP→HTTPS redirect, or change it to
`https://localhost:8443` if you prefer to proxy directly to HTTPS.

### Environment variables for Mode A

| Variable | Description | Dev value |
|---|---|---|
| `SSL_ENABLED` | Enable embedded Tomcat TLS | `true` |
| `SERVER_PORT` | HTTPS listener port | `8443` |
| `SSL_KEYSTORE_PATH` | Path to PKCS12 keystore | `classpath:ssl/hlm-dev.p12` |
| `SSL_KEYSTORE_PASSWORD` | Keystore password | `changeit` |
| `SSL_KEY_ALIAS` | Key alias inside keystore | `hlm-dev` |
| `HTTP_REDIRECT_PORT` | HTTP port that redirects to HTTPS | `8080` |

---

## 3. Staging / CI (pre-generated test keystore)

The Maven exec plugin (`exec-maven-plugin`) generates a test keystore automatically
during the `generate-test-resources` phase:

```
hlm-backend/src/test/resources/ssl/hlm-dev.p12
```

This keystore is committed to source control (test resources are not sensitive — they
contain no production keys or data). `TlsConfigIT` references it via
`server.ssl.key-store=classpath:ssl/hlm-dev.p12`.

To run the TLS integration test:

```bash
cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=TlsConfigIT
```

To skip keystore generation (e.g., if keytool is unavailable):

```bash
./mvnw test -Dssl.keystore.skip=true
```

---

## 4. Production (Nginx reverse proxy — Mode B)

### Architecture

```
Internet
   |
   | HTTPS :443 (TLS terminated here)
   |
 Nginx
   |---→ /var/www/hlm-frontend/   (Angular SPA static files)
   |---→ http://127.0.0.1:8080   (Spring Boot API, plain HTTP, local only)
```

Nginx handles TLS, serves the Angular SPA as static files, and reverse-proxies all
API requests (`/auth`, `/api`, `/dashboard`, `/actuator`) to Spring Boot.

### Step 1 — Install Nginx and obtain a certificate

```bash
# Install Nginx (Debian/Ubuntu)
sudo apt update && sudo apt install -y nginx certbot

# Obtain Let's Encrypt certificate
sudo certbot certonly --standalone -d app.yourcompany.com \
  --agree-tos -m admin@yourcompany.com --non-interactive
```

`certbot` writes the certificate to `/etc/letsencrypt/live/app.yourcompany.com/`.

### Step 2 — Deploy Nginx config

Edit `nginx/nginx.conf` (in this repository) and replace all occurrences of
`app.example.com` with your real domain. Then install it:

```bash
sudo cp nginx/nginx.conf /etc/nginx/nginx.conf
sudo nginx -t          # syntax check
sudo nginx -s reload   # apply config
```

### Step 3 — Deploy the Angular SPA

```bash
cd hlm-frontend && npm run build
sudo cp -r dist/hlm-frontend/browser/* /var/www/hlm-frontend/
```

### Step 4 — Configure Spring Boot environment

Add to your production `.env` or systemd service:

```bash
SSL_ENABLED=false                        # Nginx handles TLS; Spring listens plain HTTP
FORWARD_HEADERS_STRATEGY=FRAMEWORK       # Trust X-Forwarded-Proto from Nginx
PORTAL_BASE_URL=https://app.yourcompany.com
```

`FORWARD_HEADERS_STRATEGY=FRAMEWORK` is required so Spring Boot trusts the
`X-Forwarded-Proto: https` header sent by Nginx. Without it, Spring would believe
the connection is plain HTTP and might generate redirect URLs with `http://`.

### Step 5 — Restrict port 8080 to localhost

Spring Boot must not be reachable directly from the internet. Bind it to
`127.0.0.1` only:

```bash
# In your systemd unit or start script:
SERVER_ADDRESS=127.0.0.1
```

And ensure your firewall allows port 443 but not 8080:

```bash
sudo ufw allow 443/tcp
sudo ufw deny 8080/tcp
```

### Step 6 — Automatic certificate renewal

Add this to the system cron (`sudo crontab -e`):

```bash
0 0 1,15 * * certbot renew --pre-hook "nginx -t" --post-hook "nginx -s reload" --quiet
```

---

## 5. Security headers reference

| Header | Value | Attack prevented | Set by |
|---|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | SSL stripping / downgrade | Spring Boot (Mode A) or Nginx (Mode B) |
| `X-Frame-Options` | `DENY` | Clickjacking | Spring Boot (always) |
| `X-Content-Type-Options` | `nosniff` | MIME-type sniffing | Nginx (Mode B) |
| `X-XSS-Protection` | `1; mode=block` | Reflected XSS in older browsers | Nginx (Mode B) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referrer leakage | Nginx (Mode B) |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'` | XSS, data injection | Spring Boot (always) |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` | Sensor fingerprinting | Nginx (Mode B) |

**HSTS behaviour:** The HSTS header is only emitted by Spring Boot when
`server.ssl.enabled=true`. Sending HSTS over plain HTTP would instruct browsers to
refuse future HTTP connections to the domain — which is incorrect when TLS is
terminated externally. In Mode B (Nginx), Nginx emits the HSTS header instead.

---

## 6. Cipher suite and protocol policy

The following TLS 1.3 and TLS 1.2 cipher suites are configured for embedded Tomcat (Mode A):

```
TLS_AES_256_GCM_SHA384        (TLS 1.3)
TLS_AES_128_GCM_SHA256        (TLS 1.3)
TLS_CHACHA20_POLY1305_SHA256  (TLS 1.3)
TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384  (TLS 1.2)
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256  (TLS 1.2)
```

TLS 1.0 and TLS 1.1 are disabled (`enabled-protocols: TLSv1.2,TLSv1.3`). These
versions have known vulnerabilities (BEAST, POODLE) and are deprecated by all major
browsers and the IETF.

For Nginx (Mode B), `nginx.conf` uses `ssl_protocols TLSv1.2 TLSv1.3` and
`ssl_ciphers HIGH:!aNULL:!MD5:!RC4:!3DES`, which excludes weak anonymous suites,
MD5-based MACs, RC4 (stream cipher, biased), and triple-DES (slow, vulnerable to SWEET32).

To verify the protocol and cipher from the command line:

```bash
openssl s_client -connect localhost:8443 -tls1_2 2>/dev/null | head -20
openssl s_client -connect localhost:8443 -tls1_3 2>/dev/null | head -20
```

---

## 7. JWT security under TLS

The `Authorization: Bearer <token>` header sent with every authenticated API request
is visible in plain text to any network observer on a non-TLS connection. This means:

- In development with no TLS, token theft requires access to the local network only.
- In staging or production without TLS, any traffic on the path (corporate proxies,
  Wi-Fi hotspots, ISP equipment, BGP injection) can read or replay tokens.

HSTS `preload` ensures that browsers that have visited the site before will refuse to
send the first request over HTTP, eliminating the window for downgrade attacks.

The portal magic-link token is particularly sensitive: it is transmitted as a URL
query parameter in emails. If `PORTAL_BASE_URL` uses `http://`, the token is
transmitted in cleartext when the user clicks the link. **`PORTAL_BASE_URL` must
always begin with `https://` in any environment that handles real user data.**

---

## 8. Troubleshooting

**`javax.net.ssl.SSLHandshakeException: PKIX path building failed`**
The JVM's trust store does not contain the server's certificate or CA. For internal
CAs, import the CA cert: `keytool -importcert -trustcacerts -file ca.pem -keystore
$JAVA_HOME/lib/security/cacerts`. For tests, use a trust-all `TrustManager`
(see `TlsConfigIT`).

**`ERR_SSL_PROTOCOL_ERROR` in browser**
The configured TLS version or cipher suite is not supported by the browser (rare with
modern Chrome/Firefox). Check `server.ssl.enabled-protocols` and `server.ssl.ciphers`.

**`400 Bad Request` from Spring Boot behind reverse proxy**
Spring Boot received a request it could not route. Usually caused by a missing or wrong
`FORWARD_HEADERS_STRATEGY`. Set `FORWARD_HEADERS_STRATEGY=FRAMEWORK` and ensure Nginx
sends `proxy_set_header X-Forwarded-Proto $scheme`.

**HSTS header missing**
In Mode A: check that `SSL_ENABLED=true` — HSTS is disabled when running over plain HTTP.
In Mode B: check the `add_header Strict-Transport-Security` directive in `nginx.conf`
and verify `nginx -t` passes.

**Magic link opens `http://` instead of `https://`**
`PORTAL_BASE_URL` is set to an `http://` value. Change it to `https://` in your
production environment.

---

## 9. Quick-reference environment variable table

| Variable | Description | Dev default | Production value |
|---|---|---|---|
| `SSL_ENABLED` | Enable embedded Tomcat TLS | `false` | `false` (Mode B) or `true` (Mode A) |
| `SERVER_PORT` | HTTP or HTTPS listener port | `8080` | `8080` (Mode B) or `8443` (Mode A) |
| `SSL_KEYSTORE_PATH` | PKCS12 keystore path | `classpath:ssl/hlm-dev.p12` | N/A (Mode B) |
| `SSL_KEYSTORE_PASSWORD` | Keystore password | `changeit` | N/A (Mode B) |
| `SSL_KEY_ALIAS` | Alias inside the keystore | `hlm-dev` | N/A (Mode B) |
| `HTTP_REDIRECT_PORT` | HTTP port for redirect connector | `8080` | N/A (Mode B) |
| `FORWARD_HEADERS_STRATEGY` | How to trust forwarded headers | `NONE` | `FRAMEWORK` |
| `PORTAL_BASE_URL` | Prepended to magic-link token URLs | `http://localhost:4200` | `https://app.yourcompany.com` |
