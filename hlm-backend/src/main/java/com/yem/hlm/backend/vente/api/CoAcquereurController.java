package com.yem.hlm.backend.vente.api;

import com.yem.hlm.backend.vente.api.dto.CoAcquereurResponse;
import com.yem.hlm.backend.vente.api.dto.UpsertCoAcquereurRequest;
import com.yem.hlm.backend.vente.service.CoAcquereurService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Co-buyer (co-acquéreur) management for a vente — VEFA co-acquisition (Loi 44-00). */
@Tag(name = "Co-acquéreurs", description = "Co-buyers attached to a sale")
@RestController
@RequestMapping("/api/ventes/{venteId}/co-acquereurs")
public class CoAcquereurController {

    private final CoAcquereurService service;

    public CoAcquereurController(CoAcquereurService service) {
        this.service = service;
    }

    @GetMapping
    public List<CoAcquereurResponse> list(@PathVariable UUID venteId) {
        return service.list(venteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public CoAcquereurResponse add(@PathVariable UUID venteId,
                                   @Valid @RequestBody UpsertCoAcquereurRequest request) {
        return service.add(venteId, request);
    }

    @PutMapping("/{coId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
    public CoAcquereurResponse update(@PathVariable UUID venteId, @PathVariable UUID coId,
                                      @Valid @RequestBody UpsertCoAcquereurRequest request) {
        return service.update(venteId, coId, request);
    }

    @DeleteMapping("/{coId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void delete(@PathVariable UUID venteId, @PathVariable UUID coId) {
        service.delete(venteId, coId);
    }
}
