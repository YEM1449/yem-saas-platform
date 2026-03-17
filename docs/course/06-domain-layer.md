# Module 06 — Domain Layer

## Learning Objectives

- Describe the four-layer package structure (api, domain, repo, service)
- Write a minimal domain module from scratch
- Identify the patterns used for entities, repositories, and services

---

## Package Structure

Each domain package follows this layout:

```
{domain}/
├── api/
│   ├── {Domain}Controller.java       ← @RestController
│   └── dto/
│       ├── Create{Domain}Request.java
│       ├── Update{Domain}Request.java
│       └── {Domain}Response.java
├── domain/
│   └── {Domain}.java                 ← @Entity
├── repo/
│   └── {Domain}Repository.java       ← JpaRepository<Domain, UUID>
└── service/
    ├── {Domain}Service.java
    └── {Domain}NotFoundException.java
```

---

## Entity Pattern

```java
@Entity
@Table(name = "example")
public class Example {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_example_tenant"))
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor, getters (via Lombok @Getter), etc.
}
```

Key rules:
- UUID PK with `GenerationType.UUID` (Hibernate generates client-side).
- `Tenant` FK is `optional = false, FetchType.LAZY`.
- Audit timestamps managed by `@PrePersist` / `@PreUpdate` — no DB triggers.

---

## Repository Pattern

```java
public interface ExampleRepository extends JpaRepository<Example, UUID> {

    // Always scope to tenantId:
    List<Example> findByTenantId(UUID tenantId);

    Optional<Example> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<Example> findByTenantId(UUID tenantId, Pageable pageable);
}
```

Rule: No repository method omits the `tenantId` parameter. Cross-tenant data access via repository is impossible when this rule is followed.

---

## Service Pattern

```java
@Service
@Transactional
public class ExampleService {

    private final ExampleRepository exampleRepo;

    public ExampleService(ExampleRepository exampleRepo) {
        this.exampleRepo = exampleRepo;
    }

    public ExampleResponse get(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return exampleRepo.findByTenantIdAndId(tenantId, id)
            .map(this::toResponse)
            .orElseThrow(ExampleNotFoundException::new);
    }

    public ExampleResponse create(CreateExampleRequest req) {
        UUID tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepo.getReferenceById(tenantId);
        Example entity = new Example(tenant, req.name());
        exampleRepo.save(entity);
        return toResponse(entity);
    }

    private ExampleResponse toResponse(Example e) {
        return new ExampleResponse(e.getId(), e.getName(), e.getCreatedAt());
    }
}
```

---

## Controller Pattern

```java
@RestController
@RequestMapping("/api/examples")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class ExampleController {

    private final ExampleService exampleService;

    @GetMapping("/{id}")
    public ExampleResponse get(@PathVariable UUID id) {
        return exampleService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExampleResponse create(@Valid @RequestBody CreateExampleRequest req) {
        return exampleService.create(req);
    }
}
```

Controllers contain zero business logic. They validate, delegate, return.

---

## Source Files to Study

| File | What to observe |
|------|----------------|
| `project/domain/Project.java` | Minimal entity with tenant FK |
| `project/repo/ProjectRepository.java` | Simple tenant-scoped queries |
| `project/service/ProjectService.java` | TenantContext usage |
| `project/api/ProjectController.java` | Layered @PreAuthorize |
| `contact/domain/Contact.java` | Richer entity with enums and detail FKs |

---

## Exercise

Add a new "tag" feature to properties (a property can have zero or more text tags):

1. Write changeset `031-add-property-tags.yaml` creating a `property_tag` table with `(id, tenant_id, property_id, label)`.
2. Create `PropertyTag.java` entity.
3. Create `PropertyTagRepository` with `findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId)`.
4. Add `addTag(UUID propertyId, String label)` and `listTags(UUID propertyId)` methods to `PropertyService`.
5. Add `POST /api/properties/{id}/tags` and `GET /api/properties/{id}/tags` endpoints.
