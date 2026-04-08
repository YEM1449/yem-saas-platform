# Module 06 — Domain Layer

## Learning Objectives

- Recognize the package structure used by backend feature modules
- Understand how entities, repositories, services, and controllers divide responsibility
- Read a real module end to end using the current `societe` model
- Avoid legacy examples that still assume a `Tenant` entity reference everywhere

## What A Typical Feature Module Looks Like

Most backend domains follow this structure:

```text
{domain}/
├── api/
│   ├── {Domain}Controller.java
│   └── dto/
├── domain/
│   └── {Domain}.java
├── repo/
│   └── {Domain}Repository.java
└── service/
    ├── {Domain}Service.java
    └── domain-specific exceptions
```

Typical responsibilities:

- `api/` handles HTTP and DTO mapping boundaries
- `domain/` defines entities, enums, and invariants
- `repo/` expresses persistence access patterns
- `service/` contains business logic and transaction boundaries

## Use A Real Module As The Template

The current `project` module is a good learning anchor:

- [Project.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/domain/Project.java)
- [ProjectRepository.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/repo/ProjectRepository.java)
- [ProjectService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/service/ProjectService.java)
- [ProjectController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/api/ProjectController.java)

## Entity Pattern In The Current Codebase

Older onboarding examples sometimes showed a full `Tenant` entity relation on every aggregate. The current codebase often uses a simpler and more explicit pattern:

- persist `societeId` directly on the entity
- scope repository queries by `societeId`
- source the active société from `SocieteContext` inside the service

Example concepts from `Project`:

- UUID primary key
- `societe_id` column for ownership
- timestamps via `@PrePersist` / `@PreUpdate`
- unique constraint on `(societe_id, name)`
- archive status instead of physical delete for active business records

That is a strong default pattern for CRM domain objects in this repository.

## Repository Pattern

Repositories should be société-aware by design.

Examples from [ProjectRepository.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/repo/ProjectRepository.java):

- `findBySocieteIdAndId(...)`
- `findBySocieteIdOrderByNameAsc(...)`
- `existsBySocieteIdAndName(...)`

The repository layer is not just a convenience abstraction. It is one of the places where the platform encodes société isolation discipline.

Rule of thumb:

- do not add repository methods that can read cross-société data unless the use case is explicitly platform-level and reviewed

## Service Pattern

The service layer is where most of the real application behavior lives.

In [ProjectService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/service/ProjectService.java) you can see several common patterns:

- `@Transactional(readOnly = true)` at class level
- write methods opt into `@Transactional`
- `requireSocieteId()` resolves the active société from `SocieteContext`
- business checks happen before persistence
- DTO mapping is still centralized in the service or static DTO factory methods
- cache eviction is attached to write operations

The service layer is where the platform decides:

- what is allowed
- what is unique
- what should be cached
- what should be archived instead of deleted
- what exceptions should be exposed upward

## Controller Pattern

Controllers in this codebase should remain thin.

`ProjectController` is a good example:

- validates HTTP input
- applies `@PreAuthorize`
- delegates to the service
- translates service return values into HTTP responses

Controllers should not:

- duplicate service logic
- query repositories directly
- manually reconstruct business invariants already owned by the service

## DTO Pattern

DTOs serve two purposes:

1. shape the HTTP contract cleanly
2. decouple the entity model from API responses

Examples in the project module:

- create/update requests are separate DTO types
- response mapping is explicit
- clients do not receive raw JPA entities

This is especially important in a growing product because:

- persistence shape changes more often than API contracts
- entities may contain fields that should not be exposed
- frontend needs stable response structures

## Cross-Cutting Patterns You Should Notice

### Caching

Write methods in `ProjectService` evict `PROJECTS_CACHE`.

This shows a useful pattern:

- read caches are declared close to the service
- invalidation is tied to the mutation point, not left as an operational afterthought

### Exceptions

Service methods throw domain-specific exceptions such as:

- `ProjectNotFoundException`
- `ProjectNameAlreadyExistsException`

This keeps failure semantics readable and allows the error layer to translate them consistently.

### File and media handling

The same service also owns project-logo upload logic through `MediaStorageService`.

That is a reminder that a feature service can orchestrate adjacent infrastructure concerns without turning the controller into glue code.

## A Good End-To-End Reading Order

When learning a feature module, read it in this order:

1. controller
2. request/response DTOs
3. service
4. entity
5. repository
6. related integration tests

This order helps because you start from the user-facing API and then move inward toward persistence and invariants.

## Common Mistakes

### Mistake 1: using stale `Tenant` examples as if they were current runtime code

Historical docs and early migrations still mention `tenant`, but current business code usually scopes by `societeId`.

### Mistake 2: putting business logic in controllers

If a controller contains branching based on domain state, the logic probably belongs in a service.

### Mistake 3: adding repository methods without société scope

This is one of the easiest ways to accidentally erode isolation guarantees.

### Mistake 4: exposing entities directly over HTTP

Prefer response DTOs and explicit mapping.

## Source Files To Study

| File | What to observe |
| --- | --- |
| [Project.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/domain/Project.java) | current entity style with `societeId` and invariants |
| [ProjectRepository.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/repo/ProjectRepository.java) | société-scoped query design |
| [ProjectService.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/service/ProjectService.java) | transactions, caching, validation, orchestration |
| [ProjectController.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/project/api/ProjectController.java) | thin controller with method-level RBAC |
| [Contact.java](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/Contact.java) | richer aggregate with more lifecycle complexity |

## Exercise

Design a small new feature using the same patterns.

Suggested exercise: add “project labels”:

1. define the schema change in Liquibase
2. create a `ProjectLabel` entity under `project/domain/`
3. create a société-scoped repository
4. add service methods for create/list/delete
5. expose controller endpoints with thin HTTP wiring
6. decide where caching and validation should live

When you do the exercise, make each layer answer one question only:

- controller: what is the HTTP contract?
- service: what is allowed?
- repository: how do we fetch or persist it?
- entity: what state exists and what local invariants apply?
