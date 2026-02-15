package com.yem.hlm.backend.contact.api;

import com.yem.hlm.backend.contact.api.dto.*;
import com.yem.hlm.backend.contact.domain.ContactStatus;
import com.yem.hlm.backend.contact.domain.ContactType;
import com.yem.hlm.backend.contact.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse create(@Valid @RequestBody CreateContactRequest request) {
        return contactService.create(request);
    }

    @GetMapping("/contacts/{id}")
    public ContactResponse get(@PathVariable("id") UUID id) {
        return contactService.get(id);
    }

    @GetMapping("/contacts")
    public Page<ContactResponse> list(
            @RequestParam(value = "contactType", required = false) List<ContactType> contactType,
            @RequestParam(value = "status", required = false) ContactStatus status,
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return contactService.list(contactType, status, q, pageable);
    }

    @PatchMapping("/contacts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateContactRequest request) {
        return contactService.update(id, request);
    }

    @PatchMapping("/contacts/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContactResponse updateStatus(@PathVariable("id") UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return contactService.updateStatus(id, request.status());
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
}
