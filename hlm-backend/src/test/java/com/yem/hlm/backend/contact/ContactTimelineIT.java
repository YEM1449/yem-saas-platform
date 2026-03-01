package com.yem.hlm.backend.contact;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/contacts/{id}/timeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContactTimelineIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired CommercialAuditRepository auditRepository;

    private Tenant tenant;
    private User admin;
    private String bearer;

    @BeforeEach
    void setup() {
        String key = "tl-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantRepository.save(new Tenant(key, "Timeline Tenant"));

        admin = new User(tenant, "admin@" + key + ".com", "hash");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin = userRepository.save(admin);

        bearer = "Bearer " + jwtProvider.generate(admin.getId(), tenant.getId(), UserRole.ROLE_ADMIN);
    }

    @Test
    void timeline_emptyForNewContact_returnsEmptyArray() throws Exception {
        Contact contact = contactRepository.save(new Contact(tenant, admin.getId(), "Alice", "Smith"));

        mvc.perform(get("/api/contacts/{id}/timeline", contact.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void timeline_afterAuditEvent_returnsEvent() throws Exception {
        Contact contact = contactRepository.save(new Contact(tenant, admin.getId(), "Bob", "Dupont"));

        CommercialAuditEvent event = new CommercialAuditEvent();
        event.setTenant(tenant);
        event.setEventType(AuditEventType.DEPOSIT_CREATED);
        event.setActorUserId(admin.getId());
        event.setCorrelationId(contact.getId());
        auditRepository.save(event);

        mvc.perform(get("/api/contacts/{id}/timeline", contact.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].category").value("AUDIT"))
                .andExpect(jsonPath("$[0].eventType").value("DEPOSIT_CREATED"));
    }

    @Test
    void timeline_tenantIsolation_returns404ForOtherTenant() throws Exception {
        String otherKey = "other-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant otherTenant = tenantRepository.save(new Tenant(otherKey, "Other Tenant"));
        User otherAdmin = new User(otherTenant, "admin@" + otherKey + ".com", "hash");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepository.save(otherAdmin);
        Contact otherContact = contactRepository.save(
                new Contact(otherTenant, otherAdmin.getId(), "Eve", "Other"));

        mvc.perform(get("/api/contacts/{id}/timeline", otherContact.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void timeline_withoutToken_returns401() throws Exception {
        Contact contact = contactRepository.save(new Contact(tenant, admin.getId(), "Carl", "Anon"));

        mvc.perform(get("/api/contacts/{id}/timeline", contact.getId()))
                .andExpect(status().isUnauthorized());
    }
}
