# Naming Alignment (Spec ↔ Code)

| Spec term | Code term/artifact | Mismatch type | Recommendation | Breaking impact |
|---|---|---|---|---|
| Lot | Property (`Property`, `/api/properties`) | naming mismatch | Update business spec to canonical `Property (Lot)` alias; keep API as-is. | Non-breaking |
| Acompte / Dépôt / Réservation | Deposit aggregate (`Deposit`, `DepositStatus`, `/api/deposits`) | semantic mismatch | Decision needed: distinguish legal reservation vs accounting down payment or keep unified aggregate. | Potentially breaking (API/DB semantics) |
| Temp Client | `ContactType.PROSPECT/CLIENT` + `tempClientUntil` field | semantic mismatch | Update spec to explain temporary client as transient flag instead of standalone entity. | Non-breaking if spec-only |
| Tenant | `Tenant` + JWT `tid` + `TenantContext` | aligned | Keep `Tenant` as canonical technical term; map to société in glossary. | Non-breaking |
| Agent | `User` with role `ROLE_AGENT` | aligned | Keep role naming as in code and expose persona mapping in spec glossary. | Non-breaking |
| Prospect | `Contact` with status/type enums | naming mismatch | Spec should state Prospect is a contact state/category. | Non-breaking |
| Reservation PDF contracts | No dedicated document module in code | missing in code | Create REQ issue for document generation/storage beyond current deposit payload. | N/A |
| Admin workflow authorizations | No dedicated module in code | missing in code | Keep spec intent; create implementation issues (MOD-05). | N/A |
| Notifications | `Notification` entity + list/read APIs | partial alignment | Spec should clarify current channel is in-app only. | Non-breaking |
| Dashboard | Property summary dashboard endpoint | partial alignment | Keep dashboard section but scope to implemented metrics. | Non-breaking |

## Special attention terms
- **Acompte/Dépôt**: currently modeled as `Deposit` with status transitions and property reservation side-effects.
- **Temp Client**: represented by contact lifecycle + `tempClientUntil`, not as a standalone "TempClient" aggregate.
- **Tenant**: strict isolation anchor from JWT claim `tid`.
- **Property**: used consistently in backend/frontend code where legacy docs use "Lot".
- **Agent**: explicit RBAC role (`ROLE_AGENT`).

## Decision guidance
- Prefer **spec updates** when business intent is unchanged and code naming is internally consistent.
- Use **code refactor** only when inconsistencies exist inside implementation or contractual obligations require legacy naming.
