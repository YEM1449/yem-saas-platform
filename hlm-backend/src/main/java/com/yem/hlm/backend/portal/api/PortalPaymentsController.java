package com.yem.hlm.backend.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Portal payment-schedule endpoints — superseded by /api/portal/ventes echeancier.
 * Kept as a stub to avoid 404 surprises on old bookmarks.
 */
@Tag(name = "Portal \u2013 Payments", description = "Buyer portal payment schedule (now part of ventes tab)")
@RestController
@RequestMapping("/api/portal/contracts")
@PreAuthorize("hasRole('PORTAL')")
public class PortalPaymentsController {
}
