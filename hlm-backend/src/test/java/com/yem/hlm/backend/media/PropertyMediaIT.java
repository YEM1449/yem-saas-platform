package com.yem.hlm.backend.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.property.api.dto.PropertyCreateRequest;
import com.yem.hlm.backend.property.domain.PropertyType;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for property media upload / list / download / delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyMediaIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;

    private Tenant tenant;
    private String adminBearer;
    private String agentBearer;
    private UUID propertyId;

    @BeforeEach
    void setup() throws Exception {
        String key = "media-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantRepository.save(new Tenant(key, "Media Tenant"));

        User admin = new User(tenant, "admin@" + key + ".com", "hash");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin = userRepository.save(admin);
        adminBearer = "Bearer " + jwtProvider.generate(admin.getId(), tenant.getId(), UserRole.ROLE_ADMIN);

        User agent = new User(tenant, "agent@" + key + ".com", "hash");
        agent.setRole(UserRole.ROLE_AGENT);
        agent = userRepository.save(agent);
        agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), tenant.getId(), UserRole.ROLE_AGENT);

        Project project = projectRepository.save(new Project(tenant, "Media Test Project"));

        // Create a property via the API so it goes through normal lifecycle
        var req = new PropertyCreateRequest(
                PropertyType.VILLA, "Media Test Villa", "MEDIA-TEST-001",
                new BigDecimal("500000"), "MAD",
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, false,
                project.getId(), null
        );

        String json = mvc.perform(post("/api/properties")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        propertyId = objectMapper.readTree(json).get("id").textValue() != null
                ? UUID.fromString(objectMapper.readTree(json).get("id").textValue())
                : null;
    }

    // =========================================================================
    // Upload
    // =========================================================================

    @Test
    void upload_asAdmin_returns201WithMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.originalFilename").value("photo.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void upload_asAgent_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_disallowedType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", "bytes".getBytes());

        mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
    }

    // =========================================================================
    // List
    // =========================================================================

    @Test
    void list_afterUpload_returnsMediaEntry() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.png", "image/png", "png-data".getBytes());
        mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                .file(file)
                .header("Authorization", adminBearer))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/properties/{id}/media", propertyId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalFilename").value("img.png"))
                .andExpect(jsonPath("$[0].contentType").value("image/png"));
    }

    // =========================================================================
    // Download
    // =========================================================================

    @Test
    void download_afterUpload_returnsFileBytes() throws Exception {
        byte[] content = "jpeg-image-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "download-me.jpg", "image/jpeg", content);

        String uploadJson = mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID mediaId = UUID.fromString(objectMapper.readTree(uploadJson).get("id").textValue());

        mvc.perform(get("/api/media/{mediaId}/download", mediaId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")))
                .andExpect(content().bytes(content));
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Test
    void delete_asAdmin_returns204AndRemovesFromList() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "to-delete.jpg", "image/jpeg", "data".getBytes());
        String uploadJson = mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID mediaId = UUID.fromString(objectMapper.readTree(uploadJson).get("id").textValue());

        mvc.perform(delete("/api/media/{mediaId}", mediaId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/properties/{id}/media", propertyId)
                        .header("Authorization", agentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // Cross-tenant isolation
    // =========================================================================

    @Test
    void upload_crossTenant_returns404ForUnknownProperty() throws Exception {
        String otherKey = "other-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant otherTenant = tenantRepository.save(new Tenant(otherKey, "Other Tenant"));
        User otherAdmin = new User(otherTenant, "admin@" + otherKey + ".com", "hash");
        otherAdmin.setRole(UserRole.ROLE_ADMIN);
        otherAdmin = userRepository.save(otherAdmin);
        String otherBearer = "Bearer " + jwtProvider.generate(
                otherAdmin.getId(), otherTenant.getId(), UserRole.ROLE_ADMIN);

        // otherAdmin tries to upload to tenant's property → 404 (property not in their tenant)
        MockMultipartFile file = new MockMultipartFile(
                "file", "cross.jpg", "image/jpeg", "bytes".getBytes());
        mvc.perform(multipart("/api/properties/{id}/media", propertyId)
                        .file(file)
                        .header("Authorization", otherBearer))
                .andExpect(status().isNotFound());
    }
}
