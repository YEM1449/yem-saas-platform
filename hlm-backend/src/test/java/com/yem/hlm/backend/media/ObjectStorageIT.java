package com.yem.hlm.backend.media;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.media.service.ObjectStorageMediaStorage;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ObjectStorageMediaStorage} using a real MinIO container.
 *
 * <p>Setting {@code app.media.object-storage.enabled=true} activates
 * {@link ObjectStorageMediaStorage} (via {@code @Primary @ConditionalOnProperty})
 * and suppresses {@link com.yem.hlm.backend.media.service.LocalFileMediaStorage}.
 *
 * <p>MinIO is started via Testcontainers and provides an S3-compatible endpoint,
 * exercising the same code path used with OVH, Scaleway, Hetzner, or any other
 * S3-compatible provider in production.
 */
@SpringBootTest
@Testcontainers
class ObjectStorageIT extends IntegrationTestBase {

    @Container
    @SuppressWarnings("resource")
    static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:latest")
                    .withUserName("minioadmin")
                    .withPassword("minioadmin");

    @DynamicPropertySource
    static void objectStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.media.object-storage.enabled",    () -> "true");
        registry.add("app.media.object-storage.endpoint",   MINIO::getS3URL);
        registry.add("app.media.object-storage.access-key", MINIO::getUserName);
        registry.add("app.media.object-storage.secret-key", MINIO::getPassword);
        registry.add("app.media.object-storage.bucket",     () -> "hlm-test");
        registry.add("app.media.object-storage.region",     () -> "us-east-1");
    }

    @Autowired MediaStorageService storage;

    // =========================================================================
    // 1. Active bean is ObjectStorageMediaStorage when object-storage is enabled
    // =========================================================================

    @Test
    void storage_isObjectStorageImplementation() {
        assertThat(storage).isInstanceOf(ObjectStorageMediaStorage.class);
    }

    // =========================================================================
    // 2. Store → Load round-trip
    // =========================================================================

    @Test
    void store_thenLoad_returnsOriginalBytes() throws IOException {
        byte[] data = "hello object storage".getBytes();
        String key = storage.store(data, "test.txt", "text/plain");

        assertThat(key).isNotBlank().endsWith(".txt");

        byte[] loaded = storage.load(key).readAllBytes();
        assertThat(loaded).isEqualTo(data);
    }

    // =========================================================================
    // 3. Delete removes the object; subsequent load throws MediaNotFoundException
    // =========================================================================

    @Test
    void delete_removesObject() throws IOException {
        byte[] data = "to be deleted".getBytes();
        String key = storage.store(data, "delete-me.txt", "text/plain");

        storage.delete(key);

        // After deletion, load() throws MediaNotFoundException (wrapped as IOException)
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> storage.load(key).readAllBytes());
    }

    // =========================================================================
    // 4. Large file (> 1 MB) round-trip
    // =========================================================================

    @Test
    void store_largeFile_roundTripsSuccessfully() throws IOException {
        byte[] data = new byte[2 * 1024 * 1024]; // 2 MB
        new java.util.Random().nextBytes(data);
        String key = storage.store(data, "large.bin", "application/octet-stream");

        byte[] loaded = storage.load(key).readAllBytes();
        assertThat(loaded).isEqualTo(data);

        storage.delete(key);
    }
}
