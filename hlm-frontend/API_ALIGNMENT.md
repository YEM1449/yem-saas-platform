# API Alignment Audit

Generated: 2026-03-18
Branch: Epic/GDPR-requirements

---

## Summary

All frontend service files and TypeScript models were compared against backend
controller and DTO Java files. The following mismatches were found and corrected.

---

## Mismatches Found and Corrected

### 1. Property model (`core/models/property.model.ts`)

The frontend `Property` interface was missing many fields present in
`PropertyResponse.java`.

| Field | Was | Corrected to |
|-------|-----|--------------|
| `notes` | missing | `string \| null` |
| `commissionRate` | missing | `number \| null` |
| `estimatedValue` | missing | `number \| null` |
| `postalCode` | missing | `string \| null` |
| `latitude` | missing | `number \| null` |
| `longitude` | missing | `number \| null` |
| `titleDeedNumber` | missing | `string \| null` |
| `cadastralReference` | missing | `string \| null` |
| `ownerName` | missing | `string \| null` |
| `legalStatus` | missing | `string \| null` |
| `landAreaSqm` | missing | `number \| null` |
| `floors` | missing | `number \| null` |
| `parkingSpaces` | missing | `number \| null` |
| `hasGarden` | missing | `boolean \| null` |
| `hasPool` | missing | `boolean \| null` |
| `buildingYear` | missing | `number \| null` |
| `floorNumber` | missing | `number \| null` |
| `zoning` | missing | `string \| null` |
| `isServiced` | missing | `boolean \| null` |
| `createdBy` | missing | `string \| null` |
| `updatedBy` | missing | `string \| null` |
| `updatedAt` | missing | `string \| null` |
| `deletedAt` | missing | `string \| null` |
| `publishedAt` | missing | `string \| null` |
| `soldAt` | missing | `string \| null` |
| `reservedAt` | missing | `string \| null` |

### 2. `CreatePropertyRequest` interface (`features/properties/property.service.ts`)

The `CreatePropertyRequest` interface did not match `PropertyCreateRequest.java`.

| Field | Was | Corrected to |
|-------|-----|--------------|
| `price` | `number \| null` (optional) | `number` (required, `@NotNull`) |
| `projectId` | `string \| null` (optional) | `string` (required, `@NotNull UUID`) |
| `status` | present | **removed** — not in backend `PropertyCreateRequest` |
| `commissionRate` | missing | `number \| null` (optional) |
| `estimatedValue` | missing | `number \| null` (optional) |
| `postalCode` | missing | `string \| null` (optional) |
| `latitude` | missing | `number \| null` (optional) |
| `longitude` | missing | `number \| null` (optional) |
| `titleDeedNumber` | missing | `string \| null` (optional) |
| `cadastralReference` | missing | `string \| null` (optional) |
| `ownerName` | missing | `string \| null` (optional) |
| `legalStatus` | missing | `string \| null` (optional) |
| `landAreaSqm` | missing | `number \| null` (optional) |
| `floors` | missing | `number \| null` (optional) |
| `parkingSpaces` | missing | `number \| null` (optional) |
| `hasGarden` | missing | `boolean \| null` (optional) |
| `hasPool` | missing | `boolean \| null` (optional) |
| `buildingYear` | missing | `number \| null` (optional) |
| `floorNumber` | missing | `number \| null` (optional) |
| `zoning` | missing | `string \| null` (optional) |
| `isServiced` | missing | `boolean \| null` (optional) |
| `notes` | missing | `string \| null` (optional) |
| `buildingName` | missing | `string \| null` (optional) |

`UpdatePropertyRequest` interface added (was missing entirely), matching
`PropertyUpdateRequest.java`.

`getById()`, `update()`, and `delete()` methods added to `PropertyService`
(were missing).

### 3. Property type enum values (`properties.component.ts` and `properties.component.html`)

The component used invalid `PropertyType` enum values that do not exist in
`PropertyType.java`.

| Was | Corrected to |
|-----|--------------|
| `APARTMENT` | `APPARTEMENT` |
| `HOUSE` | removed |
| `PENTHOUSE` | removed |
| `LAND` | `TERRAIN` |
| `OFFICE` | removed |
| `COMMERCIAL` | `COMMERCE` |
| `OTHER` | removed |
| (missing) | `T2`, `T3`, `LOCAL` added |

Full correct set: `VILLA, APPARTEMENT, STUDIO, T2, T3, DUPLEX, COMMERCE, LOCAL, TERRAIN`

The CSV format reference table and example rows in `properties.component.html`
were also corrected to use valid type values.

### 4. `submitCreate()` in `properties.component.ts`

The `status` field was being sent to the backend in the create call, but
`PropertyCreateRequest.java` has no `status` field. Removed from the payload.

### 5. Contact model (`core/models/contact.model.ts`) — GDPR fields missing

`ContactResponse.java` includes four GDPR/Law 09-08 consent fields that were
absent from the frontend model.

| Field | Was | Corrected to |
|-------|-----|--------------|
| `consentGiven` | missing | `boolean` |
| `consentDate` | missing | `string \| null` |
| `consentMethod` | missing | `string \| null` |
| `processingBasis` | missing | `string \| null` |

### 6. Prospect model (`core/models/prospect.model.ts`) — GDPR fields missing

Same GDPR fields added to `Prospect` interface (prospects are contacts fetched
from the same `/api/contacts` endpoint which returns `ContactResponse`).

### 7. `CreateContactRequest` in `contact.service.ts`

The frontend interface had a `contactType` field not present in backend's
`CreateContactRequest.java`, and was missing several fields.

| Field | Was | Corrected to |
|-------|-----|--------------|
| `contactType` | `string` (present) | **removed** — not in backend DTO |
| `email` | `string \| null` (required) | `string \| null` (optional) |
| `phone` | `string \| null` (required) | `string \| null` (optional) |
| `notes` | `string \| null` (required) | `string \| null` (optional) |
| `nationalId` | missing | `string \| null` (optional) |
| `address` | missing | `string \| null` (optional) |
| `consentGiven` | missing | `boolean \| null` (optional, GDPR) |
| `consentMethod` | missing | `string \| null` (optional, GDPR) |
| `processingBasis` | missing | `string \| null` (optional, GDPR) |

### 8. `DepositReportResponse` model (`core/models/deposit.model.ts`)

| Field | Was | Corrected to |
|-------|-----|--------------|
| `byAgent` | `unknown[]` | `DepositReportByAgent[]` (new typed interface added) |

`DepositReportByAgent` interface added with fields: `agentId`, `agentEmail`,
`count`, `totalAmount`.

---

## Models Updated

- `hlm-frontend/src/app/core/models/property.model.ts`
- `hlm-frontend/src/app/core/models/contact.model.ts`
- `hlm-frontend/src/app/core/models/prospect.model.ts`
- `hlm-frontend/src/app/core/models/deposit.model.ts`
- `hlm-frontend/src/app/features/properties/property.service.ts`
- `hlm-frontend/src/app/features/contacts/contact.service.ts`
- `hlm-frontend/src/app/features/properties/properties.component.ts`
- `hlm-frontend/src/app/features/properties/properties.component.html`

---

## Endpoints Where Backend Contract Was Clear — No Issues

| Service | Endpoint | Status |
|---------|----------|--------|
| `PropertyService.list()` | `GET /api/properties` | Returns `List<PropertyResponse>` — matches |
| `PropertyService.importCsv()` | `POST /api/properties/import` | Returns `ImportResultResponse` — matches |
| `ContactService.list()` | `GET /api/contacts` | Returns `Page<ContactResponse>` — `ContactPage` interface matches |
| `ProspectService.list()` | `GET /api/contacts?contactType=PROSPECT&contactType=TEMP_CLIENT` | Correct multi-value params |
| `ProjectService.list()` | `GET /api/projects?activeOnly=...` | Correct |
| `ContractService.list()` | `GET /api/contracts` | Returns `List<ContractResponse>` — matches |
| `OutboxService.send()` | `POST /api/messages` | Returns `SendMessageResponse` — matches |
| `OutboxService.list()` | `GET /api/messages` | Returns `Page<MessageResponse>` — `OutboundMessagePage` matches |
| `ReservationService.list()` | `GET /api/reservations` | Matches |
| `CommissionService` | `/api/commissions`, `/api/commission-rules` | Uses proxy-relative URLs — matches |
| `ReceivablesDashboardService` | `/api/dashboard/receivables` | Matches `ReceivablesDashboardDTO` |
| `DepositService.create()` | `POST /api/deposits` | Matches `CreateDepositRequest.java` |

---

## Items Flagged for Review

1. **`CommissionService` uses proxy-relative URLs** (`/api/commissions`) while
   all other services use `environment.apiUrl + '/api/...'`. This works in
   local dev via the Angular proxy but may fail if the API base URL changes.
   Recommend aligning to `environment.apiUrl` pattern.

2. **`ReceivablesDashboardService` uses proxy-relative URL** (`/api/dashboard/receivables`).
   Same concern as above.

3. **`ContactService` is missing `update()` method** for `PATCH /api/contacts/:id`.
   The backend supports it but no frontend service method exists.

4. **`ContactService` is missing `convertToProspect()` and `convertToClient()` methods**.
   These endpoints exist in `ContactController.java` but are not exposed in the service.

5. **`ContractService` is missing `create()` method** — `CreateContractRequest.java`
   exists in the backend but no create method in the frontend service.
