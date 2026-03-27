package com.yem.hlm.backend.contract.template.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-société custom PDF template.
 *
 * <p>When a société provides a custom template for a given {@link TemplateType},
 * it is rendered via Thymeleaf string processing instead of the built-in classpath template.
 * The HTML content may contain any Thymeleaf expressions — the same {@code model} variable
 * is exposed as in the classpath templates.
 *
 * <p>At most one template per (societeId, templateType) pair is allowed (enforced by
 * the {@code uk_contract_template} unique constraint).
 */
@Entity
@Table(
        name = "contract_template",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contract_template",
                columnNames = {"societe_id", "template_type"}
        )
)
public class ContractTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 30)
    private TemplateType templateType;

    @Column(name = "html_content", nullable = false, columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected ContractTemplate() {}

    public ContractTemplate(UUID societeId, TemplateType templateType, String htmlContent) {
        this.societeId    = societeId;
        this.templateType = templateType;
        this.htmlContent  = htmlContent;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getId()                  { return id; }
    public UUID getSocieteId()           { return societeId; }
    public TemplateType getTemplateType(){ return templateType; }
    public String getHtmlContent()       { return htmlContent; }
    public void setHtmlContent(String h) { this.htmlContent = h; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
    public Long getVersion()             { return version; }
}
