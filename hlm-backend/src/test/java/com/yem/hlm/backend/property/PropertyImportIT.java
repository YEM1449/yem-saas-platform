package com.yem.hlm.backend.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/properties/import (CSV bulk import).
 *
 * <p>Verifies: successful import, row-level error rejection (all-or-nothing),
 * RBAC (AGENT cannot import), and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyImportIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;

    private String adminBearer;
    private String agentBearer;
    private UUID projectId;

    /** CSV header row exactly matching PropertyImportService.HEADERS. */
    private static final String CSV_HEADER =
            "referenceCode,projectId,type,title,price,surfaceArea,landArea," +
            "bedrooms,bathrooms,floor,building,status";

    @BeforeEach
    void setup() {
        String key = "import-" + UUID.randomUUID().toString().substring(0, 8);
        Societe societe = societeRepository.save(new Societe("Import Test Societe", "MA"));

        User admin = new User("admin@" + key + ".com", "hash");
        admin = userRepository.save(admin);
        adminBearer = "Bearer " + jwtProvider.generate(admin.getId(), societe.getId(), UserRole.ROLE_ADMIN);

        User agent = new User("agent@" + key + ".com", "hash");
        agent = userRepository.save(agent);
        agentBearer = "Bearer " + jwtProvider.generate(agent.getId(), societe.getId(), UserRole.ROLE_AGENT);

        Project project = projectRepository.save(new Project(societe.getId(), "Import Test Project"));
        projectId = project.getId();
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void import_validCsv_returns200WithImportedCount() throws Exception {
        String csv = CSV_HEADER + "\n" +
                "IMP-001," + projectId + ",VILLA,Villa Alpha,750000,200,400,4,2,,,\n" +
                "IMP-002," + projectId + ",APPARTEMENT,Apt Beta,300000,80,,2,1,3,,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/properties/import")
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void import_asManager_returns200() throws Exception {
        String key2 = "import2-" + UUID.randomUUID().toString().substring(0, 8);
        Societe t2 = societeRepository.save(new Societe("Import T2 Societe", "MA"));
        User manager = new User("mgr@" + key2 + ".com", "hash");
        manager = userRepository.save(manager);
        Project p2 = projectRepository.save(new Project(t2.getId(), "P2"));
        String mgrBearer = "Bearer " + jwtProvider.generate(manager.getId(), t2.getId(), UserRole.ROLE_MANAGER);

        String csv = CSV_HEADER + "\n" +
                "MGR-001," + p2.getId() + ",APPARTEMENT,Mgr Apt,200000,80,,2,1,3,,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/properties/import")
                        .file(file)
                        .header("Authorization", mgrBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    // =========================================================================
    // Validation — all-or-nothing
    // =========================================================================

    @Test
    void import_rowWithMissingRequiredField_returns422WithErrors() throws Exception {
        // Row 2 has blank referenceCode (required)
        String csv = CSV_HEADER + "\n" +
                "GOOD-001," + projectId + ",VILLA,Good Villa,500000,,,,,,,\n" +
                "," + projectId + ",APARTMENT,Missing Ref,200000,,,,,,,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/properties/import")
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.errors[0].row").isNumber())
                .andExpect(jsonPath("$.errors[0].message").isString());
    }

    @Test
    void import_rowWithInvalidType_returns422() throws Exception {
        String csv = CSV_HEADER + "\n" +
                "BAD-TYPE," + projectId + ",NOT_A_TYPE,Invalid Property,100000,,,,,,,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad-type.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/properties/import")
                        .file(file)
                        .header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.errors[0].message", containsString("type")));
    }

    // =========================================================================
    // RBAC
    // =========================================================================

    @Test
    void import_asAgent_returns403() throws Exception {
        String csv = CSV_HEADER + "\n" +
                "AGT-001," + projectId + ",VILLA,Villa,500000,,,,,,,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/properties/import")
                        .file(file)
                        .header("Authorization", agentBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void import_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", "".getBytes());

        mvc.perform(multipart("/api/properties/import").file(file))
                .andExpect(status().isUnauthorized());
    }
}
