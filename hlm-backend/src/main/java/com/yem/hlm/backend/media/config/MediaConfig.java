package com.yem.hlm.backend.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers media-related configuration properties beans.
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class MediaConfig {
}
