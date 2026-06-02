package com.yem.hlm.backend.viewer3d.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Server-side binary validation of a GLB (binary glTF) stream — RG-E05.
 *
 * <p>Never trusts the client {@code dracoCompressed} flag: it reads the actual bytes,
 * verifies the GLB container (magic {@code "glTF"} + version 2 + JSON chunk), then parses
 * the JSON chunk and requires {@code KHR_draco_mesh_compression} in {@code extensionsUsed}
 * or {@code extensionsRequired}.
 *
 * <p>Only a bounded prefix is read (the JSON chunk lives at the start of the file; the
 * Draco-compressed geometry is in the trailing BIN chunk), so callers can pass a streamed
 * object and let this method consume only what it needs.
 *
 * <p>GLB layout (little-endian): 12-byte header [magic u32 | version u32 | length u32],
 * then chunk 0 [chunkLength u32 | chunkType u32 = "JSON" | chunkLength bytes of JSON].
 */
public final class GlbValidator {

    /** glTF magic as little-endian u32: bytes 'g','l','T','F'. */
    private static final int GLB_MAGIC = 0x46546C67;
    /** "JSON" chunk type as little-endian u32. */
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;
    private static final String DRACO_EXTENSION = "KHR_draco_mesh_compression";
    /** Upper bound on the JSON chunk we will buffer/parse (8 MiB — far above real scene JSON). */
    private static final int MAX_JSON_BYTES = 8 * 1024 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GlbValidator() {}

    /**
     * Validates the GLB stream. Throws {@link InvalidGlbException} on any structural problem
     * or when Draco compression is absent. The stream is read but not closed.
     */
    public static void validate(InputStream in) throws IOException {
        byte[] header = in.readNBytes(12);
        if (header.length < 12) {
            throw new InvalidGlbException("Fichier GLB invalide : en-tête tronqué (" + header.length + " octets).");
        }
        if (readU32(header, 0) != GLB_MAGIC) {
            throw new InvalidGlbException("Fichier GLB invalide : signature glTF absente.");
        }
        int version = readU32(header, 4);
        if (version != 2) {
            throw new InvalidGlbException("Version GLB non supportée : " + version + " (attendu : 2).");
        }

        byte[] chunkHeader = in.readNBytes(8);
        if (chunkHeader.length < 8) {
            throw new InvalidGlbException("Fichier GLB invalide : en-tête de chunk JSON manquant.");
        }
        long chunkLength = readU32(chunkHeader, 0) & 0xFFFFFFFFL;
        if (readU32(chunkHeader, 4) != CHUNK_TYPE_JSON) {
            throw new InvalidGlbException("Fichier GLB invalide : le premier chunk n'est pas de type JSON.");
        }
        if (chunkLength == 0 || chunkLength > MAX_JSON_BYTES) {
            throw new InvalidGlbException("Fichier GLB invalide : taille du chunk JSON hors limites (" + chunkLength + ").");
        }

        byte[] jsonBytes = in.readNBytes((int) chunkLength);
        if (jsonBytes.length < chunkLength) {
            throw new InvalidGlbException("Fichier GLB invalide : chunk JSON tronqué.");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(new String(jsonBytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidGlbException("Fichier GLB invalide : chunk JSON illisible.");
        }
        if (!hasDracoExtension(root)) {
            throw new InvalidGlbException(
                    "Le modèle 3D doit être compressé avec Draco (extension "
                    + DRACO_EXTENSION + " absente du glTF).");
        }
    }

    private static boolean hasDracoExtension(JsonNode root) {
        return arrayContains(root.get("extensionsRequired"), DRACO_EXTENSION)
                || arrayContains(root.get("extensionsUsed"), DRACO_EXTENSION);
    }

    private static boolean arrayContains(JsonNode array, String value) {
        if (array == null || !array.isArray()) return false;
        for (JsonNode n : array) {
            if (value.equals(n.asText())) return true;
        }
        return false;
    }

    /** Reads a little-endian unsigned 32-bit int from {@code buf} at {@code offset}. */
    private static int readU32(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }
}
