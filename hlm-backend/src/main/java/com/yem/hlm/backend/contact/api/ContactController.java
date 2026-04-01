package com.yem.hlm.backend.contact.api;

import com.yem.hlm.backend.common.dto.PageResponse;
import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import com.yem.hlm.backend.contact.service.ContactService;
import com.yem.hlm.backend.contact.service.ContactTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Contacts", description = "Contact lifecycle management — prospects, clients, interests")
@Validated
@RestController
@RequestMapping("/api")
public class ContactController {

    private final ContactService contactService;
    private final ContactTimelineService timelineService;

    public ContactController(ContactService contactService,
                             ContactTimelineService timelineService) {
        this.contactService  = contactService;
        this.timelineService = timelineService;
    }

    @Operation(summary = "Create a new contact (ADMIN/MANAGER only)")
    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse create(@Valid @RequestBody CreateContactRequest request) {
        return contactService.create(request);
    }

    @Operation(summary = "Get a contact by ID")
    @GetMapping("/contacts/{id}")
    public ContactResponse get(@PathVariable("id") UUID id) {
        return contactService.get(id);
    }

    @Operation(summary = "List contacts with optional filtering by type, status and search term")
    @GetMapping("/contacts")
    public PageResponse<ContactResponse> list(
            @RequestParam(value = "contactType", required = false) List<ContactType> contactType,
            @RequestParam(value = "status", required = false) ContactStatus status,
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return PageResponse.of(contactService.list(contactType, status, q, pageable));
    }

    @Operation(summary = "Partially update a contact's fields (ADMIN/MANAGER only)")
    @PatchMapping("/contacts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateContactRequest request) {
        return contactService.update(id, request);
    }

    /**
     * Qualifies a contact as a QUALIFIED_PROSPECT and enriches ProspectDetail
     * with optional budget / source data. ADMIN/MANAGER only.
     */
    @PostMapping("/contacts/{id}/convert-to-prospect")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse convertToProspect(
            @PathVariable UUID id,
            @RequestBody(required = false) ConvertToProspectRequest request
    ) {
        return contactService.convertToProspect(id,
                request != null ? request : new ConvertToProspectRequest(null, null, null, null));
    }

    @PostMapping("/contacts/{id}/convert-to-client")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse convert(@PathVariable("id") UUID id, @Valid @RequestBody ConvertToClientRequest request) {
        return contactService.convertToClient(id, request);
    }

    @PostMapping("/contacts/{id}/interests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void addInterest(@PathVariable("id") UUID id, @Valid @RequestBody ContactInterestRequest request) {
        contactService.addInterest(id, request);
    }

    @DeleteMapping("/contacts/{id}/interests/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeInterest(@PathVariable("id") UUID id, @PathVariable UUID propertyId) {
        contactService.removeInterest(id, propertyId);
    }

    @GetMapping("/contacts/{id}/interests")
    public List<ContactInterestResponse> listInterests(@PathVariable("id") UUID id) {
        return contactService.listInterestsForContact(id);
    }

    @GetMapping("/properties/{propertyId}/contacts")
    public List<UUID> listContactsForProperty(@PathVariable UUID propertyId) {
        return contactService.listContactsForProperty(propertyId);
    }

    /**
     * Unified activity timeline for a contact.
     * Aggregates audit events, outbox messages, and in-app notifications.
     * RBAC: all authenticated roles.
     */
    @GetMapping("/contacts/{id}/timeline")
    public List<TimelineEventResponse> getTimeline(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return timelineService.getTimeline(id, limit);
    }
}
