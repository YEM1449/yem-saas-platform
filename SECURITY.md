# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main`  | ✅ Active |
| Others  | ❌ Not supported |

---

## Threat Model

### Assets

| Asset | Sensitivity | Notes |
|-------|-------------|-------|
| Contact PII (name, phone, email) | High | GDPR / Moroccan Law 09-08 |
| Contract financial data | High | Société-confidential |
| JWT tokens | High | 1-hour TTL, server-side revocation |
| User passwords | Critical | BCrypt + StrongPassword validator |
| Platform admin credentials | Critical | SUPER_ADMIN role |

### Trust Boundaries

```
Internet
  │  HTTPS only (TLS 1.2/1.3), HSTS preload
  ▼
Nginx (reverse proxy)
  │  CSP, X-Frame-Options, HSTS, X-Content-Type-Options
  │  Permissions-Policy, Referrer-Policy
  ▼
Spring Boot (8080, localhost-only)
  │  JWT authentication, rate limiting, RLS context
  ▼
PostgreSQL
     RLS policies (societe_id isolation per row)
```

### Attacker Profiles

| Profile | Capability | Mitigations |
|---------|-----------|-------------|
| Unauthenticated internet user | Brute-force, enumeration | Rate limiting (IP + identity), account lockout, timing-attack mitigation |
| Authenticated AGENT | Horizontal privilege escalation (cross-société) | RLS + JPA dual enforcement, `requireSocieteId()` |
| Authenticated ADMIN | Vertical privilege escalation | `@PreAuthorize` + role hierarchy, no self-promotion to SUPER_ADMIN |
| Compromised JWT | Token replay | Token version revocation, 1-hour TTL |
| SUPER_ADMIN | Impersonation abuse | `imp` JWT claim, audit trail in `SecurityAuditLogger` |

---

## Security Controls Map

### Authentication

| Control | Implementation | File |
|---------|---------------|------|
| JWT HS256 (min 32-byte key) | `NimbusJwtEncoder` / `JwtDecoder` | `JwtBeansConfig.java` |
| Fail-fast on weak secret | `@NotBlank @Size(min=32)` | `JwtProperties.java` |
| Token version revocation | `tv` JWT claim vs DB | `JwtAuthenticationFilter.java` |
| Account lockout (5 attempts, 15 min) | `User.recordFailedAttempt()` | `AuthService.java` |
| Login rate limit (dual-bucket) | Bucket4j, IP + identity | `LoginRateLimiter.java` |
| Timing-attack mitigation | Dummy BCrypt on unknown email | `AuthService.java` |
| Password strength (12+ chars, complexity) | `@StrongPassword` | `StrongPasswordValidator.java` |
| BCrypt password hashing | Spring `BCryptPasswordEncoder` | `SecurityBeansConfig.java` |

### Authorisation

| Control | Implementation | File |
|---------|---------------|------|
| Role-based access (`ADMIN`/`MANAGER`/`AGENT`/`PORTAL`/`SUPER_ADMIN`) | `@PreAuthorize` + `SecurityConfig` | `SecurityConfig.java` |
| Multi-société isolation | `SocieteContext` ThreadLocal | `JwtAuthenticationFilter.java` |
| Database-layer isolation | PostgreSQL RLS (changeset 051) | `RlsContextAspect.java` |
| Async context propagation | `SocieteContextTaskDecorator` | `AsyncConfig.java` |

### Transport

| Control | Value | File |
|---------|-------|------|
| TLS versions | 1.2 + 1.3 only | `application.yml`, `nginx.conf` |
| HSTS | `max-age=31536000; includeSubDomains; preload` | `SecurityConfig.java`, `nginx.conf` |
| CSP | `default-src 'self'; script-src 'self'; frame-ancestors 'none'` | `SecurityConfig.java` |
| X-Frame-Options | `DENY` | `SecurityConfig.java` |
| X-Content-Type-Options | `nosniff` | `SecurityConfig.java` |
| X-XSS-Protection | `1; mode=block` | `SecurityConfig.java` |
| Referrer-Policy | `strict-origin-when-cross-origin` | `SecurityConfig.java` |
| Permissions-Policy | geo/mic/cam/payment disabled | `SecurityConfig.java` |

### Data Protection

| Control | Implementation |
|---------|---------------|
| GDPR / Law 09-08 | Data retention scheduler, soft-delete, anonymisation |
| PII in logs | `SecurityAuditLogger.maskEmail()` masks all email addresses |
| Secrets in config | All secrets via environment variables; fail-fast on blank JWT secret |
| No source maps in production | `sourceMap: false` in `angular.json` production config |

---

## Dependency Audit Process

1. **Automated (CI):** `snyk.yml` workflow runs on every push/PR and weekly.
   Blocks the build if a CVSS ≥ 7.0 vulnerability is found in a direct dependency.

2. **Manual:** Run locally before a release:
   ```bash
   # Backend
   cd hlm-backend && ./mvnw dependency-check:check

   # Frontend
   cd hlm-frontend && npm audit --audit-level=high
   ```

3. **Known overrides** (documented in `pom.xml`):
   | Package | Override Version | CVE mitigated |
   |---------|-----------------|---------------|
   | `org.liquibase:liquibase-core` | `5.0.0` | CWE-674 via commons-lang3 (CVSS 8.8) |
   | `org.jetbrains.kotlin:kotlin-stdlib` | `2.1.0` | kotlin-stdlib vulnerability |

---

## Vulnerability Disclosure Policy

**Scope:** This policy covers the `yem-saas-platform` repository and its deployed instances.

**Reporting:** Email `security@yem.ma` with:
- Description of the vulnerability
- Steps to reproduce
- Affected version / commit hash
- Impact assessment

**Response SLA:**
| Severity | Acknowledgement | Fix target |
|----------|----------------|-----------|
| Critical | 24 hours | 72 hours |
| High | 48 hours | 7 days |
| Medium/Low | 5 days | 30 days |

**Safe harbour:** We will not take legal action against researchers who report vulnerabilities in good faith, avoid data exfiltration, and do not disrupt service.

**Out of scope:** Social engineering, physical attacks, DoS, spam.

---

## Security Findings Log

| Date | Severity | Finding | Status |
|------|----------|---------|--------|
| 2026-03-26 | MED | Email enumeration via login timing difference | ✅ Fixed — dummy BCrypt on unknown email |
| 2026-03-26 | LOW | Missing `X-XSS-Protection` header in Spring config | ✅ Fixed — `HeaderValue.ENABLED_MODE_BLOCK` |
| 2026-03-26 | LOW | CSP missing `frame-ancestors`, `form-action`, `base-uri` | ✅ Fixed — directives added to `SecurityConfig` |
| 2026-03-26 | LOW | `Permissions-Policy` used deprecated API | ✅ Fixed — `StaticHeadersWriter` |
| 2026-03-26 | INFO | `index.html` title was "Frontend" (information leak) | ✅ Fixed — renamed to "HLM CRM" |
