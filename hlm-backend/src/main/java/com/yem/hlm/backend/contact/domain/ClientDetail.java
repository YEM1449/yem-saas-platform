package com.yem.hlm.backend.contact.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "client_detail")
public class ClientDetail implements Persistable<UUID> {

    @Id
    @Column(name = "contact_id")
    private UUID contactId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, foreignKey = @ForeignKey(name = "fk_client_detail_contact"))
    private Contact contact;

    @Transient
    private boolean isNewEntity = true;

    @Override
    public UUID getId() {
        return contactId;
    }

    @Override
    public boolean isNew() {
        return isNewEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNewEntity = false;
    }

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "client_kind", length = 32)
    private ClientKind clientKind;

    @Setter
    @Column(name = "company_name", length = 180)
    private String companyName;

    @Setter
    @Column(name = "ice", length = 32)
    private String ice;

    @Setter
    @Column(name = "siret", length = 32)
    private String siret;

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

    public ClientDetail(Contact contact) {
        this.contact = contact;
        this.contactId = contact.getId();
    }
}
