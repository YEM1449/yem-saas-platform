package com.yem.hlm.backend.groupe.api;

import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.GroupClient;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.LinkCandidate;
import com.yem.hlm.backend.groupe.api.dto.GroupClientDtos.LinkClientsRequest;
import com.yem.hlm.backend.groupe.service.GroupClientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Group client identity (#005) — recognise the same buyer across an owner's sociétés.
 * ADMIN only; every operation is scoped to the caller's ADMIN sociétés in the service layer.
 */
@Tag(name = "Clients Groupe", description = "Cross-société client identity linking (consent-based)")
@RestController
@RequestMapping("/api/groupe/clients")
@PreAuthorize("hasRole('ADMIN')")
public class GroupClientController {

    private final GroupClientService service;

    public GroupClientController(GroupClientService service) {
        this.service = service;
    }

    /** Same-CIN contacts found in ≥2 of the owner's sociétés — link suggestions. */
    @GetMapping("/candidates")
    public List<LinkCandidate> candidates() {
        return service.findCandidates();
    }

    /** Established group-person clusters across the owner's sociétés. */
    @GetMapping
    public List<GroupClient> list() {
        return service.listGroupClients();
    }

    /** Links contacts as one group person (requires consent). */
    @PostMapping("/link")
    public GroupClient link(@Valid @RequestBody LinkClientsRequest request) {
        return service.link(request);
    }

    /** Removes a contact from its group cluster (consent withdrawal). */
    @DeleteMapping("/{contactId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID contactId) {
        service.unlink(contactId);
    }
}
