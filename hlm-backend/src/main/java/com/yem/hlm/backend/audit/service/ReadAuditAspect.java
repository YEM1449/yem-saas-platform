package com.yem.hlm.backend.audit.service;

import com.yem.hlm.backend.common.event.SensitiveDataReadEvent;
import com.yem.hlm.backend.societe.SocieteContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Intercepts methods annotated with {@link ReadAudit} and publishes a
 * {@link SensitiveDataReadEvent} to the audit log after the method returns (B-004).
 *
 * <p>The entity ID is extracted from the first {@code UUID} argument of the method
 * (typically the path-variable {@code id} / {@code contactId} / etc.).
 * The event is published synchronously but handled in a REQUIRES_NEW transaction by
 * {@link AuditEventListener}, so audit recording never rolls back with the caller.
 */
@Aspect
@Component
public class ReadAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(ReadAuditAspect.class);

    private final ApplicationEventPublisher eventPublisher;

    public ReadAuditAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("@annotation(readAudit)")
    public Object audit(ProceedingJoinPoint pjp, ReadAudit readAudit) throws Throwable {
        Object result = pjp.proceed();

        try {
            UUID societeId = SocieteContext.getSocieteId();   // null for portal/super-admin
            UUID actorId   = SocieteContext.getUserId();
            UUID entityId  = extractFirstUuid(pjp.getArgs());

            if (actorId != null && entityId != null) {
                eventPublisher.publishEvent(
                        new SensitiveDataReadEvent(societeId, actorId,
                                readAudit.entityType(), entityId));
            }
        } catch (Exception e) {
            // Audit must never break the original request
            log.warn("[READ-AUDIT] Failed to publish audit event for {}: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod().getName(), e.getMessage());
        }

        return result;
    }

    private static UUID extractFirstUuid(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof UUID u) return u;
        }
        return null;
    }
}
