package com.yem.hlm.backend.contact;

import com.yem.hlm.backend.audit.domain.AuditEventType;
import com.yem.hlm.backend.audit.domain.CommercialAuditEvent;
import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.contact.domain.Contact;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.societe.SocieteRepository;
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
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ContactRepository contactRepository;
    @Autowired CommercialAuditRepository auditRepository;

    private Societe societe;
    private User admin;
    private String bearer;

    @BeforeEach
    void setup() {
        String key = "tl-" + UUID.randomUUID().toString().substring(0, 8);
        societe = societeRepository.save(new Societe("Acme Corp", "MA"));

        admin = new User("admin@" + key + ".com", "hash");
        admin = userRepository.save(admin);

        bearer = "Bearer " + jwtProvider.generate(admin.getId(), societe.getId(), UserRole.ROLE_ADMIN);
    }

    @Test
    void timeline_emptyForNewContact_returnsEmptyArray() throws Exception {
        Contact contact = contactRepository.save(new Contact(societe.getId(), admin.getId(), "Alice", "Smith"));

        mvc.perform(get("/api/contacts/{id}/timeline", contact.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void timeline_afterAuditEvent_returnsEvent() throws Exception {
        Contact contact = contactRepository.save(new Contact(societe.getId(), admin.getId(), "Bob", "Dupont"));

        CommercialAuditEvent event = new CommercialAuditEvent();
        event.setSocieteId(societe.getId());
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
        Societe otherTenant = societeRepository.save(new Societe("Acme Corp", "MA"));
        User otherAdmin = new User("admin@" + otherKey + ".com", "hash");
        otherAdmin = userRepository.save(otherAdmin);
        Contact otherContact = contactRepository.save(
                new Contact(otherTenant.getId(), otherAdmin.getId(), "Eve", "Other"));

        mvc.perform(get("/api/contacts/{id}/timeline", otherContact.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void timeline_withoutToken_returns401() throws Exception {
        Contact contact = contactRepository.save(new Contact(societe.getId(), admin.getId(), "Carl", "Anon"));

        mvc.perform(get("/api/contacts/{id}/timeline", contact.getId()))
                .andExpect(status().isUnauthorized());
    }
}
