package com.yem.hlm.backend.viewer3d;

import com.yem.hlm.backend.viewer3d.service.GlbValidator;
import com.yem.hlm.backend.viewer3d.service.InvalidGlbException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GlbValidator} — RG-E05 server-side GLB binary validation.
 * Docker-free: GLB byte fixtures are crafted in-memory.
 */
class GlbValidatorTest {

    private static final int GLB_MAGIC = 0x46546C67;       // 'glTF' LE
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A; // 'JSON' LE

    private static final String DRACO_JSON =
            "{\"asset\":{\"version\":\"2.0\"},"
            + "\"extensionsUsed\":[\"KHR_draco_mesh_compression\"],"
            + "\"extensionsRequired\":[\"KHR_draco_mesh_compression\"]}";
    private static final String NO_DRACO_JSON =
            "{\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"KHR_materials_unlit\"]}";

    // ── helpers ────────────────────────────────────────────────────────────────

    private static byte[] glb(int magic, int version, int chunkType, String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU32(out, magic);
        writeU32(out, version);
        writeU32(out, 12 + 8 + jsonBytes.length); // total length
        writeU32(out, jsonBytes.length);          // chunk0 length
        writeU32(out, chunkType);                 // chunk0 type
        out.writeBytes(jsonBytes);
        return out.toByteArray();
    }

    private static byte[] validDracoGlb() {
        return glb(GLB_MAGIC, 2, CHUNK_TYPE_JSON, DRACO_JSON);
    }

    private static void writeU32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void validate(byte[] bytes) throws Exception {
        GlbValidator.validate(new ByteArrayInputStream(bytes));
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid Draco-compressed GLB passes")
    void validDracoGlb_passes() {
        assertThatCode(() -> validate(validDracoGlb())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("GLB with Draco only in extensionsUsed passes")
    void dracoInExtensionsUsedOnly_passes() {
        String json = "{\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"KHR_draco_mesh_compression\"]}";
        assertThatCode(() -> validate(glb(GLB_MAGIC, 2, CHUNK_TYPE_JSON, json)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Bad glTF magic is rejected")
    void badMagic_rejected() {
        assertThatThrownBy(() -> validate(glb(0xDEADBEEF, 2, CHUNK_TYPE_JSON, DRACO_JSON)))
                .isInstanceOf(InvalidGlbException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Unsupported container version is rejected")
    void wrongVersion_rejected() {
        assertThatThrownBy(() -> validate(glb(GLB_MAGIC, 1, CHUNK_TYPE_JSON, DRACO_JSON)))
                .isInstanceOf(InvalidGlbException.class)
                .hasMessageContaining("Version");
    }

    @Test
    @DisplayName("First chunk not of type JSON is rejected")
    void notJsonChunk_rejected() {
        assertThatThrownBy(() -> validate(glb(GLB_MAGIC, 2, 0x004E4942 /* 'BIN\\0' */, DRACO_JSON)))
                .isInstanceOf(InvalidGlbException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("GLB without the Draco extension is rejected")
    void noDracoExtension_rejected() {
        assertThatThrownBy(() -> validate(glb(GLB_MAGIC, 2, CHUNK_TYPE_JSON, NO_DRACO_JSON)))
                .isInstanceOf(InvalidGlbException.class)
                .hasMessageContaining("Draco");
    }

    @Test
    @DisplayName("Truncated header is rejected")
    void truncatedHeader_rejected() {
        assertThatThrownBy(() -> validate(new byte[]{0x67, 0x6C, 0x54}))
                .isInstanceOf(InvalidGlbException.class)
                .hasMessageContaining("tronqué");
    }
}
