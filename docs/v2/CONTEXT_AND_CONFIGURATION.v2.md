# Context and Configuration v2

This file standardizes runtime/configuration context for developers and operators.

## 1. Core Runtime Variables
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `DB_URL` | Yes | `jdbc:postgresql://localhost:5432/hlm` | JDBC connection |
| `DB_USER` | Yes | `hlm_user` | DB user |
| `DB_PASSWORD` | Yes | `hlm_pwd` | DB password |
| `JWT_SECRET` | Yes | none | JWT signing secret (min 32 chars) |
| `JWT_TTL_SECONDS` | No | `3600` | CRM JWT TTL |

## 2. Email/Outbox Variables
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `EMAIL_HOST` | No | empty | SMTP host |
| `EMAIL_PORT` | No | `587` | SMTP port |
| `EMAIL_USER` | No | empty | SMTP username |
| `EMAIL_PASSWORD` | No | empty | SMTP password |
| `EMAIL_FROM` | No | `noreply@example.com` | Sender address |
| `OUTBOX_BATCH_SIZE` | No | `20` | Dispatch batch size |
| `OUTBOX_MAX_RETRIES` | No | `3` | Retry cap |
| `OUTBOX_POLL_INTERVAL_MS` | No | `5000` | Poll interval |

## 3. Reminder and Scheduling Variables
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `PAYMENTS_OVERDUE_CRON` | No | `0 0 6 * * *` | Overdue marking schedule |
| `REMINDER_ENABLED` | No | `true` | Reminder scheduler switch |
| `REMINDER_CRON` | No | `0 0 8 * * *` | Reminder run schedule |
| `REMINDER_DEPOSIT_WARN_DAYS` | No | `7,3,1` | Deposit warning offsets |
| `REMINDER_PROSPECT_STALE_DAYS` | No | `14` | Prospect inactivity threshold |

## 4. Media and CORS Variables
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `MEDIA_STORAGE_DIR` | No | `./uploads` | Local media storage root |
| `MEDIA_MAX_FILE_SIZE` | No | `10485760` | Max upload size |
| `MEDIA_ALLOWED_TYPES` | No | image/pdf list | Allowed MIME types |
| `CORS_ALLOWED_ORIGINS` | No | empty | Allowed CORS origins |

## 5. Portal Variable
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `app.portal.base-url` | No | `http://localhost:4200` | Portal magic-link base URL |

Note: this value is configured through Spring property binding (not currently mapped to a dedicated uppercase env alias in docs).

## 6. Context Best Practices
1. Never commit secrets in docs or source.
2. Keep environment names aligned with `application.yml`.
3. Treat production defaults as explicit, not implicit.
4. Document every new variable in both runtime docs and context references.
