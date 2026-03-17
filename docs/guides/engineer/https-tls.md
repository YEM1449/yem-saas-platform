# HTTPS / TLS Guide — Engineer Guide

This guide explains the two TLS modes supported by the platform, when to use each, and how to configure them.

## Table of Contents

1. [TLS Modes Overview](#tls-modes-overview)
2. [Mode A — Embedded Tomcat TLS](#mode-a--embedded-tomcat-tls)
3. [Mode B — Nginx TLS Termination](#mode-b--nginx-tls-termination)
4. [Generate a Development Certificate](#generate-a-development-certificate)
5. [Production Certificate (Let's Encrypt)](#production-certificate-lets-encrypt)
6. [HSTS Configuration](#hsts-configuration)
7. [Verifying TLS](#verifying-tls)

---

## TLS Modes Overview

| Mode | When to use | `SSL_ENABLED` | Nginx role |
|------|------------|--------------|-----------|
| A — Embedded Tomcat TLS | Direct backend exposure, dev/staging self-signed cert | `true` | Not used for TLS |
| B — Nginx TLS termination | Production; Nginx handles certs + TLS; backend plain HTTP | `false` | Handles TLS, proxies plain HTTP to backend |

Mode B is the recommended production setup. Nginx manages the certificate lifecycle (e.g., Let's Encrypt via Certbot) and forwards plain HTTP traffic to the backend on port 8080.

---

## Mode A — Embedded Tomcat TLS

### Configuration

Set in `.env`:
```bash
SSL_ENABLED=true
SSL_KEYSTORE_PATH=/path/to/your.p12
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=hlm
```

Spring Boot `application.yml` maps these:
```yaml
server:
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${SSL_KEYSTORE_PATH:classpath:ssl/hlm-dev.p12}
    key-store-type: PKCS12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-alias: ${SSL_KEY_ALIAS:hlm-dev}
    enabled-protocols: TLSv1.2,TLSv1.3
```

Supported cipher suites (TLS 1.3 preferred):
```
TLS_AES_256_GCM_SHA384
TLS_AES_128_GCM_SHA256
TLS_CHACHA20_POLY1305_SHA256
TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
```

### HTTP to HTTPS redirect

`TlsRedirectConfig` adds a plain HTTP connector on `HTTP_REDIRECT_PORT` (default 8443) that issues a 302 redirect to HTTPS. Set in `.env`:
```bash
HTTP_REDIRECT_PORT=8443
```

With `SSL_ENABLED=true`, the `SecurityConfig` automatically enables HSTS:
```java
h.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31536000)
    .preload(true));
```

---

## Mode B — Nginx TLS Termination

This is the recommended setup for production. Nginx handles TLS, and the backend runs on plain HTTP.

### Backend configuration

```bash
SSL_ENABLED=false
FORWARD_HEADERS_STRATEGY=FRAMEWORK
```

`FORWARD_HEADERS_STRATEGY=FRAMEWORK` tells Spring to trust `X-Forwarded-Proto: https` from Nginx. This is required so that:
- `HttpServletRequest.isSecure()` returns `true`.
- Spring Security generates `https://` redirect URLs.
- HSTS is properly applied in security headers.

### Nginx configuration snippet

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:...;

    location /api/ {
        proxy_pass http://hlm-backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}
```

---

## Generate a Development Certificate

For Mode A local development, generate a self-signed PKCS12 keystore:

```bash
keytool -genkeypair \
  -alias hlm-dev \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore hlm-dev.p12 \
  -validity 3650 \
  -storepass changeit \
  -dname "CN=localhost, OU=Dev, O=HLM, L=Casablanca, S=Casablanca, C=MA"
```

Place the generated `hlm-dev.p12` at `hlm-backend/src/main/resources/ssl/hlm-dev.p12`.

The default `application.yml` references this classpath resource:
```yaml
key-store: ${SSL_KEYSTORE_PATH:classpath:ssl/hlm-dev.p12}
```

**Note:** The Docker build stage does NOT generate this keystore. It must be added to the source tree or injected at runtime via `SSL_KEYSTORE_PATH` pointing to a mounted file.

---

## Production Certificate (Let's Encrypt)

### With Certbot (standalone)

```bash
certbot certonly --standalone -d your-domain.com
```

Certificates are written to `/etc/letsencrypt/live/your-domain.com/`.

### With Certbot (Nginx plugin)

```bash
certbot --nginx -d your-domain.com
```

Certbot modifies the Nginx config to add TLS settings automatically.

### Auto-renewal

Certbot installs a cron or systemd timer for automatic renewal. Verify:

```bash
certbot renew --dry-run
```

---

## HSTS Configuration

HSTS is only emitted when `SSL_ENABLED=true` (Mode A). In Mode B, Nginx should set the HSTS header:

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
```

The platform's `SecurityConfig` emits HSTS from Spring Security only in Mode A:

```java
if (sslEnabled) {
    http.headers(h -> h
        .httpStrictTransportSecurity(hsts -> hsts
            .includeSubDomains(true)
            .maxAgeInSeconds(31536000)
            .preload(true)));
}
```

---

## Verifying TLS

### Check TLS with openssl

```bash
# Check certificate and TLS handshake
openssl s_client -connect your-domain.com:443 -servername your-domain.com

# Check specific TLS version
openssl s_client -connect your-domain.com:443 -tls1_3
```

### Check cipher suites with nmap

```bash
nmap --script ssl-enum-ciphers -p 443 your-domain.com
```

### Check HSTS header

```bash
curl -I https://your-domain.com | grep -i strict-transport
```

Expected output:
```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

### Check with testssl.sh

```bash
./testssl.sh your-domain.com
```

This provides a comprehensive report including cipher grade, HSTS, HPKP, and vulnerability checks.
