package com.yem.hlm.backend.tenant.domain; // Package: organise le code (namespace Java)

import jakarta.persistence.*;              // Annotations JPA/Hibernate (@Entity, @Id, etc.)
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;                     // Lombok: génère le setter automatiquement pour le champ annoté

import java.util.UUID;                    // Type UUID pour identifiants uniques
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity // JPA: marque cette classe comme entité persistée en base (table)
@Table(
        name = "tenant", // Nom exact de la table en DB
        uniqueConstraints = {
                // Contrainte unique: interdit deux tenants avec la même "key"
                @UniqueConstraint(name = "uk_tenant_name", columnNames = {"key"})
        }
)
public class Tenant {

    @Id // JPA: indique la clé primaire
    @GeneratedValue(strategy = GenerationType.UUID) // Hibernate génère automatiquement un UUID
    private UUID id; // Identifiant technique du tenant (PK)

    @Column(name = "key", nullable = false, length = 80)
    // "key" = identifiant fonctionnel du tenant (ex: "acme") utilisé dans ton SaaS multi-tenant
    private String key;

    @Setter // Lombok: autorise la modification de "name" via setName(...)
    @Column(name = "name", nullable = false, length = 160)
    // "name" = nom affiché du tenant (ex: "Acme Corporation")
    private String name;

    // Constructeur métier: toi tu crées un Tenant avec les infos minimales
    public Tenant(String key, String name) {
        this.key = key;   // on stocke la key fonctionnelle (tenantKey)
        this.name = name; // on stocke le nom affiché
    }

    // Getters: exposent les valeurs (lecture). (Tu peux aussi utiliser Lombok @Getter si tu veux.)
    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
}
