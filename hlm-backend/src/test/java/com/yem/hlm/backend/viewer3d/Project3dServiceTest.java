package com.yem.hlm.backend.viewer3d;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.project.domain.Project;
import com.yem.hlm.backend.project.repo.ProjectRepository;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.viewer3d.api.dto.*;
import com.yem.hlm.backend.viewer3d.domain.Lot3dMapping;
import com.yem.hlm.backend.viewer3d.domain.Project3dModel;
import com.yem.hlm.backend.viewer3d.repo.Lot3dMappingRepository;
import com.yem.hlm.backend.viewer3d.repo.Project3dModelRepository;
import com.yem.hlm.backend.viewer3d.service.Project3dModelNotFoundException;
import com.yem.hlm.backend.viewer3d.service.Project3dService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Project3dServiceTest {

    private static final UUID SOCIETE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID    = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock Project3dModelRepository modelRepository;
    @Mock Lot3dMappingRepository   mappingRepository;
    @Mock PropertyRepository       propertyRepository;
    @Mock ProjectRepository        projectRepository;
    @Mock MediaStorageService      storageService;

    @InjectMocks Project3dService service;

    @BeforeEach
    void setUp() {
        SocieteContext.setSocieteId(SOCIETE_ID);
        SocieteContext.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SocieteContext.clear();
    }

    // ── upsertModel ───────────────────────────────────────────────────────────

    @Test
    void upsertModel_createsNewRecord_whenNoneExists() throws IOException {
        Project project = stubProject();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(modelRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.generatePresignedUrl(any(), any())).thenReturn("https://r2.example.com/glb");
        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of());

        Project3dModelResponse response = service.upsertModel(PROJECT_ID,
                new Create3dModelRequest("models/s/p/file.glb", true));

        assertThat(response.glbPresignedUrl()).isEqualTo("https://r2.example.com/glb");
        assertThat(response.projetId()).isEqualTo(PROJECT_ID);
        assertThat(response.dracoCompressed()).isTrue();
        verify(modelRepository).save(any(Project3dModel.class));
    }

    @Test
    void upsertModel_throwsProjectNotFound_whenProjectBelongsToOtherSociete() {
        Project project = stubProject(UUID.randomUUID()); // different société
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.upsertModel(PROJECT_ID,
                new Create3dModelRequest("models/s/p/file.glb", true)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── getModel ──────────────────────────────────────────────────────────────

    @Test
    void getModel_returnsResponseWithPresignedUrl() throws IOException {
        Project3dModel model = stubModel();
        when(modelRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(Optional.of(model));
        when(storageService.generatePresignedUrl(anyString(), any())).thenReturn("https://r2/signed");
        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of());

        Project3dModelResponse response = service.getModel(PROJECT_ID);

        assertThat(response.glbPresignedUrl()).isEqualTo("https://r2/signed");
        assertThat(response.mappings()).isEmpty();
    }

    @Test
    void getModel_throws404_whenNoModelExists() {
        when(modelRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getModel(PROJECT_ID))
                .isInstanceOf(Project3dModelNotFoundException.class);
    }

    // ── getStatusSnapshot ─────────────────────────────────────────────────────

    @Test
    void getStatusSnapshot_returnsEmptyList_whenNoMappings() {
        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of());

        List<Lot3dStatusDto> result = service.getStatusSnapshot(PROJECT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getStatusSnapshot_mapsPropertyStatusToDisplayStatus() {
        UUID propId = UUID.randomUUID();
        Lot3dMapping mapping = new Lot3dMapping(SOCIETE_ID, PROJECT_ID, propId,
                "Appart_A101", null, null);

        Property property = stubProperty(propId, PropertyStatus.RESERVED,
                PropertyType.APPARTEMENT, new BigDecimal("72.50"), new BigDecimal("285000"));

        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of(mapping));
        when(propertyRepository.findAllBySocieteIdAndIdInAndDeletedAtIsNull(eq(SOCIETE_ID), any()))
                .thenReturn(List.of(property));

        List<Lot3dStatusDto> statuses = service.getStatusSnapshot(PROJECT_ID);

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).statut()).isEqualTo("RESERVE");
        assertThat(statuses.get(0).meshId()).isEqualTo("Appart_A101");
    }

    @Test
    void getStatusSnapshot_displaysDisponible_forActiveProperty() {
        UUID propId = UUID.randomUUID();
        Lot3dMapping mapping = new Lot3dMapping(SOCIETE_ID, PROJECT_ID, propId, "Mesh1", null, null);

        Property property = stubProperty(propId, PropertyStatus.ACTIVE, null, null, null);

        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of(mapping));
        when(propertyRepository.findAllBySocieteIdAndIdInAndDeletedAtIsNull(eq(SOCIETE_ID), any()))
                .thenReturn(List.of(property));

        List<Lot3dStatusDto> statuses = service.getStatusSnapshot(PROJECT_ID);

        assertThat(statuses.get(0).statut()).isEqualTo("DISPONIBLE");
    }

    @Test
    void getStatusSnapshot_displaysRetire_forWithdrawnProperty() {
        UUID propId = UUID.randomUUID();
        Lot3dMapping mapping = new Lot3dMapping(SOCIETE_ID, PROJECT_ID, propId, "Mesh2", null, null);

        Property property = stubProperty(propId, PropertyStatus.WITHDRAWN, null, null, null);

        when(mappingRepository.findBySocieteIdAndProjetId(SOCIETE_ID, PROJECT_ID))
                .thenReturn(List.of(mapping));
        when(propertyRepository.findAllBySocieteIdAndIdInAndDeletedAtIsNull(eq(SOCIETE_ID), any()))
                .thenReturn(List.of(property));

        List<Lot3dStatusDto> statuses = service.getStatusSnapshot(PROJECT_ID);

        assertThat(statuses.get(0).statut()).isEqualTo("RETIRE");
    }

    // ── generateUploadUrl ─────────────────────────────────────────────────────

    @Test
    void generateUploadUrl_returnsPutUrl_forValidRequest() throws IOException {
        Project project = stubProject();
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(storageService.generatePresignedPutUrl(any(), any())).thenReturn("https://r2/put-url");

        UploadUrlResponse response = service.generateUploadUrl(PROJECT_ID,
                new UploadUrlRequest("building.glb", 10_000_000L, true));

        assertThat(response.uploadUrl()).isEqualTo("https://r2/put-url");
        assertThat(response.fileKey()).startsWith("models/" + SOCIETE_ID + "/" + PROJECT_ID + "/");
        assertThat(response.fileKey()).endsWith(".glb");
    }

    @Test
    void generateUploadUrl_rejects_whenDracoCompressedFalse() {
        assertThatThrownBy(() -> service.generateUploadUrl(PROJECT_ID,
                new UploadUrlRequest("building.glb", 5_000_000L, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draco");
    }

    @Test
    void generateUploadUrl_rejectsFileSizeAbove50Mb() {
        // Service checks societeId matches context; wrong société → ProjectNotFoundException
        Project project = stubProject(UUID.randomUUID()); // wrong société
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.generateUploadUrl(PROJECT_ID,
                new UploadUrlRequest("building.glb", 5_000_000L, true)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Project stubProject() {
        return stubProject(SOCIETE_ID);
    }

    private Project stubProject(UUID societeId) {
        Project p = mock(Project.class);
        lenient().when(p.getId()).thenReturn(PROJECT_ID);
        when(p.getSocieteId()).thenReturn(societeId);
        return p;
    }

    private Project3dModel stubModel() {
        Project3dModel m = new Project3dModel(SOCIETE_ID, PROJECT_ID,
                "models/s/p/file.glb", true, USER_ID);
        return m;
    }

    private Property stubProperty(UUID id, PropertyStatus status,
                                   PropertyType type, BigDecimal surface, BigDecimal price) {
        Property p = mock(Property.class);
        when(p.getId()).thenReturn(id);
        when(p.getStatus()).thenReturn(status);
        if (type    != null) when(p.getType()).thenReturn(type);
        if (surface != null) when(p.getSurfaceAreaSqm()).thenReturn(surface);
        if (price   != null) when(p.getPrice()).thenReturn(price);
        return p;
    }
}
