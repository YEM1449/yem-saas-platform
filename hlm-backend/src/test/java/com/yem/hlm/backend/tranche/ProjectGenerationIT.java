package com.yem.hlm.backend.tranche;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.support.IntegrationTestBase;
import com.yem.hlm.backend.tranche.api.dto.*;
import com.yem.hlm.backend.tranche.repo.TrancheRepository;
import com.yem.hlm.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the "generate project" wizard endpoint.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Happy path: 2 tranches × 2 buildings each → correct unit counts</li>
 *   <li>Auth guards (401/403)</li>
 *   <li>Validation errors (missing required fields)</li>
 *   <li>Tranche CRUD and statut transition</li>
 * </ul>
 *
 * <p>No {@code @Transactional} — AuditEventListener uses {@code REQUIRES_NEW}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectGenerationIT extends IntegrationTestBase {

    private static final UUID SOCIETE_ID   = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc          mvc;
    @Autowired ObjectMapper     mapper;
    @Autowired JwtProvider      jwtProvider;
    @Autowired TrancheRepository trancheRepo;
    @Autowired PropertyRepository propertyRepo;

    private String adminBearer;
    private String agentBearer;
    private String uid;

    @BeforeEach
    void setup() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        adminBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_ADMIN);

        // Reuse ADMIN_USER_ID (exists in seed DB) but set role to ROLE_AGENT in the JWT.
        // The security filter validates user existence + tokenVersion (both pass), but
        // Spring Security then denies the request with 403 due to insufficient role.
        agentBearer = "Bearer " + jwtProvider.generate(ADMIN_USER_ID, SOCIETE_ID, UserRole.ROLE_AGENT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth guards
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generate_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/projects/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generate_asAgent_returns403() throws Exception {
        mvc.perform(post("/api/projects/generate")
                        .header("Authorization", agentBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generate_missingProjectNom_returns400() throws Exception {
        mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectVille\":\"Marseille\",\"tranches\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_missingVille_returns400() throws Exception {
        mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectNom\":\"Test\",\"tranches\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generate_singleTrancheOneBuildingFourFloors_createsExpectedUnits() throws Exception {
        // 1 tranche, 1 building (R+4, no RDC), 4 floors × 2 appartements = 8 units
        FloorConfig floor = new FloorConfig(1, "APPARTEMENT", 2, null, null, null, "SUD");
        BuildingConfig building = new BuildingConfig(0, null, 4, false, "NONE", 0, false, 0,
                List.of(floor, floor, floor, floor));
        TrancheConfig tranche = new TrancheConfig(1, null,
                LocalDate.of(2026, 12, 31), null, null, List.of(building));

        ProjectGenerationRequest req = new ProjectGenerationRequest(
                "Résidence Test " + uid,
                null, null, "Marseille", null,
                "LETTRE", "Bâtiment", "BUILDING_FLOOR_UNIT",
                null, "RDC", false, "P", false,
                List.of(tranche));

        String responseBody = mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.tranchesGenerated").value(1))
                .andExpect(jsonPath("$.buildingsGenerated").value(1))
                .andExpect(jsonPath("$.unitsGenerated").value(8))
                .andReturn().getResponse().getContentAsString();

        JsonNode resp = mapper.readTree(responseBody);
        UUID projectId = UUID.fromString(resp.get("projectId").asText());

        // Verify tranche was persisted
        var savedTranches = trancheRepo.findBySocieteIdAndProjectIdOrderByNumeroAsc(
                SOCIETE_ID, projectId);
        assertThat(savedTranches).hasSize(1);
        assertThat(savedTranches.get(0).getNumero()).isEqualTo(1);

        // Verify properties were persisted
        var props = propertyRepo.findWithFilters(SOCIETE_ID, projectId, null, null, null);
        assertThat(props).hasSize(8);
        assertThat(props).allMatch(p -> p.getTrancheId() != null);
        assertThat(props).allMatch(p -> p.getImmeuble() != null);
    }

    @Test
    void generate_twoTranchesWithBuildings_bothTranchesCreated() throws Exception {
        FloorConfig floor = new FloorConfig(1, "APPARTEMENT", 2, null, null, null, null);
        BuildingConfig b1 = new BuildingConfig(0, null, 3, false, "NONE", 0, false, 0, List.of(floor, floor, floor));
        BuildingConfig b2 = new BuildingConfig(1, null, 2, false, "NONE", 0, false, 0, List.of(floor, floor));

        TrancheConfig t1 = new TrancheConfig(1, "Tranche A", LocalDate.of(2026, 6, 30),
                null, null, List.of(b1));
        TrancheConfig t2 = new TrancheConfig(2, "Tranche B", LocalDate.of(2027, 12, 31),
                null, null, List.of(b2));

        ProjectGenerationRequest req = new ProjectGenerationRequest(
                "Résidence 2T " + uid, null, null, "Casablanca", null,
                "LETTRE", "Bâtiment", "BUILDING_FLOOR_UNIT",
                null, "RDC", false, "P", false,
                List.of(t1, t2));

        String body = mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tranchesGenerated").value(2))
                .andExpect(jsonPath("$.buildingsGenerated").value(2))
                // t1: 1 building × 3 floors × 2 = 6; t2: 1 building × 2 floors × 2 = 4 → 10
                .andExpect(jsonPath("$.unitsGenerated").value(10))
                .andReturn().getResponse().getContentAsString();

        UUID projectId = UUID.fromString(mapper.readTree(body).get("projectId").asText());
        var tranches = trancheRepo.findBySocieteIdAndProjectIdOrderByNumeroAsc(SOCIETE_ID, projectId);
        assertThat(tranches).hasSize(2);
        assertThat(tranches.get(0).getDisplayNom()).isEqualTo("Tranche A");
        assertThat(tranches.get(1).getDisplayNom()).isEqualTo("Tranche B");
    }

    @Test
    void generate_withParking_createsParking() throws Exception {
        FloorConfig floor = new FloorConfig(1, "APPARTEMENT", 2, null, null, null, null);
        BuildingConfig b = new BuildingConfig(0, null, 2, false, "NONE", 0, true, 5,
                List.of(floor, floor));
        TrancheConfig t = new TrancheConfig(1, null, LocalDate.of(2027, 1, 1),
                null, null, List.of(b));

        ProjectGenerationRequest req = new ProjectGenerationRequest(
                "Résidence Parking " + uid, null, null, "Agadir", null,
                "LETTRE", "Bâtiment", "BUILDING_FLOOR_UNIT",
                null, "RDC", true, "P", true, List.of(t));

        mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                // 5 parking + 2 floors × 2 = 9
                .andExpect(jsonPath("$.unitsGenerated").value(9))
                .andExpect(jsonPath("$.unitsByType.PARKING").value(5));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tranche statut lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void listTranches_returnsCreatedTranches() throws Exception {
        String body = generate2TranchesProject();
        UUID projectId = UUID.fromString(mapper.readTree(body).get("projectId").asText());

        mvc.perform(get("/api/projects/{id}/tranches", projectId)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void advanceStatut_enPreparationToEnCommercialisation_succeeds() throws Exception {
        String body = generate2TranchesProject();
        JsonNode resp = mapper.readTree(body);
        UUID projectId = UUID.fromString(resp.get("projectId").asText());
        UUID trancheId = UUID.fromString(resp.get("tranches").get(0).get("trancheId").asText());

        mvc.perform(patch("/api/projects/{pid}/tranches/{tid}/statut", projectId, trancheId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"EN_COMMERCIALISATION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COMMERCIALISATION"));
    }

    @Test
    void advanceStatut_invalidSkip_returns409() throws Exception {
        String body = generate2TranchesProject();
        JsonNode resp = mapper.readTree(body);
        UUID projectId = UUID.fromString(resp.get("projectId").asText());
        UUID trancheId = UUID.fromString(resp.get("tranches").get(0).get("trancheId").asText());

        // Skip from EN_PREPARATION directly to EN_TRAVAUX (must pass EN_COMMERCIALISATION first)
        mvc.perform(patch("/api/projects/{pid}/tranches/{tid}/statut", projectId, trancheId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"EN_TRAVAUX\"}"))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String generate2TranchesProject() throws Exception {
        FloorConfig floor = new FloorConfig(1, "APPARTEMENT", 1, null, null, null, null);
        BuildingConfig b = new BuildingConfig(0, null, 2, false, "NONE", 0, false, 0, List.of(floor, floor));
        TrancheConfig t1 = new TrancheConfig(1, null, LocalDate.of(2026, 6, 30), null, null, List.of(b));
        BuildingConfig b2 = new BuildingConfig(1, null, 2, false, "NONE", 0, false, 0, List.of(floor));
        TrancheConfig t2 = new TrancheConfig(2, null, LocalDate.of(2027, 6, 30), null, null, List.of(b2));

        ProjectGenerationRequest req = new ProjectGenerationRequest(
                "Gen2T-" + uid, null, null, "Rabat", null,
                "LETTRE", "Bâtiment", "BUILDING_FLOOR_UNIT",
                null, "RDC", false, "P", false, List.of(t1, t2));

        return mvc.perform(post("/api/projects/generate")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String validRequestJson() {
        return """
                {
                  "projectNom":"ValidProject",
                  "projectVille":"Marrakech",
                  "tranches":[{
                    "numero":1,
                    "dateLivraisonPrevue":"2026-12-31",
                    "buildings":[{
                      "buildingOrder":0,"floorCount":3,"hasRdc":false,
                      "rdcType":"NONE","rdcUnitCount":0,"hasParking":false,"parkingCount":0,
                      "floors":[{"floorNumber":1,"propertyType":"APPARTEMENT","unitCount":2}]
                    }]
                  }]
                }
                """;
    }
}
