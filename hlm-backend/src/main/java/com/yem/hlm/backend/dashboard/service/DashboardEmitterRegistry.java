package com.yem.hlm.backend.dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active SSE emitters for real-time dashboard updates.
 * Keys are {@code societeId:sessionId} to enable per-société broadcast.
 */
@Component
public class DashboardEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(DashboardEmitterRegistry.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID societeId, String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        String key = societeId + ":" + sessionId;
        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        emitter.onError(e -> emitters.remove(key));
        return emitter;
    }

    public void broadcast(UUID societeId, String eventName, Object data) {
        String prefix = societeId + ":";
        emitters.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) return false;
            try {
                entry.getValue().send(SseEmitter.event().name(eventName).data(data));
                return false;
            } catch (IOException e) {
                log.debug("SSE emitter removed for key={}: {}", entry.getKey(), e.getMessage());
                return true;
            }
        });
    }
}
