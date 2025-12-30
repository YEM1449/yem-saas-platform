package com.yem.hlm.backend.tenant.domain;

import jakarta.persistence.*;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tenant",uniqueConstraints = {
        @UniqueConstraint(name="uk_tenant_name", columnNames = {"key"})
})
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key", nullable = false, length = 80)
    private String key;

    @Setter
    @Column(name = "name", nullable = false, length = 160)
    private String name;

    protected void tenant() {}

    public Tenant(String key, String name) {
        this.key = key;
        this.name = name;
    }
    public UUID getId() {return id;}
    public String getKey() {return key;}
    public String getName() {return name;}

}
