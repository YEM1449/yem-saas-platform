package com.yem.hlm.backend.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.societe.SocieteRepository;
import com.yem.hlm.backend.societe.domain.Societe;
import com.yem.hlm.backend.support.IntegrationTestBase;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired SocieteRepository societeRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;

    private String adminBearer;
    private UUID projectId;

    @BeforeEach
    void setup() {
        String key = "doc-" + UUID.randomUUID().toString().substring(0, 8);
        Societe societe = societeRepository.save(new Societe("Document Test Societe", "MA"));

        User admin = new User("admin@" + key + ".com", "hash");
        admin = userRepository.save(admin);
        adminBearer = "Bearer " + jwtProvider.generate(admin.getId(), societe.getId(), UserRole.ROLE_ADMIN);

        Project project = projectRepository.save(new Project(societe.getId(), "Document Test Project"));
        projectId = project.getId();
    }

    @Test
    void upload_allowedPdf_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "pdf-bytes".getBytes());

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("entityType", "PROJECT")
                        .param("entityId", projectId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));
    }

    @Test
    void upload_svgDocument_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "vector.svg", "image/svg+xml", "<svg></svg>".getBytes());

        mvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("entityType", "PROJECT")
                        .param("entityId", projectId.toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
    }
}
