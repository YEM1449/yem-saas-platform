# Module 18 — Rate Limiting and Account Lockout

## Learning Objectives

- Describe the two Bucket4j buckets used for login rate limiting
- Explain the token bucket algorithm
- Trace the account lockout flow for failed logins

---

## Bucket4j Token Bucket Algorithm

Bucket4j implements the token bucket algorithm. A bucket has:
- **Capacity** — maximum number of tokens (requests allowed in the window)
- **Refill** — tokens added back per period

A request "consumes" one token. When the bucket is empty, the request is rejected with HTTP 429 and a `Retry-After` header indicating when the next token will be available.

---

## Login Rate Limiting

Two independent buckets are checked for every login attempt:

### 1. IP Bucket

| Setting | Default | Env Var |
|---------|---------|---------|
| Capacity | 20 | `RATE_LIMIT_LOGIN_IP_MAX` |
| Window | 60 s | `RATE_LIMIT_LOGIN_WINDOW_SECONDS` |
| Key | Client IP address | — |

Prevents brute-force from a single IP.

### 2. Identity Bucket

| Setting | Default | Env Var |
|---------|---------|---------|
| Capacity | 10 | `RATE_LIMIT_LOGIN_KEY_MAX` |
| Window | 60 s | `RATE_LIMIT_LOGIN_WINDOW_SECONDS` |
| Key | Email address | — |

Prevents distributed brute-force against a specific account from many IPs.

If **either** bucket is empty → `LoginRateLimitedException` → HTTP 429 + `Retry-After` header.

---

## Portal Magic Link Rate Limiting

| Setting | Default | Env Var |
|---------|---------|---------|
| Capacity | 3 | `RATE_LIMIT_PORTAL_CAPACITY` |
| Period | 1 hour | `RATE_LIMIT_PORTAL_REFILL_PERIOD` |
| Key | Client IP | — |

Prevents abuse of the magic link email sending endpoint.

---

## Account Lockout

Beyond rate limiting, the system also applies per-account lockout after repeated failures:

```java
// In AuthService.login():

// 1. Check if locked
if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
    throw new AccountLockedException(user.getLockedUntil());
}

// 2. Try authentication
boolean success = passwordEncoder.matches(password, user.getPasswordHash());

// 3. Update counters
if (success) {
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
} else {
    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
    if (user.getFailedLoginAttempts() >= lockoutMaxAttempts) {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
    }
}
```

| Setting | Default | Env Var |
|---------|---------|---------|
| Max attempts | 5 | `LOCKOUT_MAX_ATTEMPTS` |
| Lockout duration | 15 min | `LOCKOUT_DURATION_MINUTES` |

---

## Difference: Rate Limit vs Lockout

| Mechanism | Scope | Key | Storage |
|-----------|-------|-----|---------|
| IP rate limit | Per IP | IP address | In-memory Bucket4j |
| Identity rate limit | Per email | Email | In-memory Bucket4j |
| Account lockout | Per user account | `app_user.locked_until` | Database |

Rate limits reset on refill period. Account lockout persists in the DB and must expire (or be manually cleared by admin).

---

## Source Files

| File | Purpose |
|------|---------|
| `auth/service/LoginRateLimiter.java` | IP and identity bucket management |
| `auth/service/AuthService.java` | Lockout check and update |
| `auth/config/LoginRateLimitProperties.java` | Rate limit config properties |
| `auth/config/LockoutProperties.java` | Lockout config properties |
| `common/ratelimit/RateLimiterService.java` | General-purpose bucket service |

---

## Exercise

1. Open `LoginRateLimiter.java` and identify the two `Bandwidth` objects (IP bucket and identity bucket).
2. Calculate: with default settings, how many login attempts from the same IP can be made before the IP is blocked? (20 per 60 seconds)
3. What happens if attacker uses 3 different IPs with the same email? How many total attempts before the identity bucket is empty? (3 IPs × max_per_IP = 60 attempts, but identity bucket = 10 per 60 s — so identity bucket is the tighter limit)
4. Set `LOCKOUT_MAX_ATTEMPTS=3` in `.env`, try 4 failed logins, and verify the 4th returns `ACCOUNT_LOCKED`.
