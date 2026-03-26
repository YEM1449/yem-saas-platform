package com.yem.hlm.backend.user.api;

import com.yem.hlm.backend.user.api.dto.UpdateProfileRequest;
import com.yem.hlm.backend.user.api.dto.UserProfileResponse;
import com.yem.hlm.backend.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service profile endpoint — any authenticated CRM user can read and update their own profile.
 *
 * <pre>
 * GET   /api/me  — return the caller's profile
 * PATCH /api/me  — update editable profile fields (null = no change)
 * </pre>
 */
@Tag(name = "Profile", description = "Self-service user profile management")
@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'AGENT')")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Get own profile", description = "Returns the authenticated user's full profile including société role.")
    @GetMapping
    public UserProfileResponse getProfile() {
        return profileService.getProfile();
    }

    @Operation(summary = "Update own profile", description = "Partially updates the authenticated user's profile. Null fields are ignored.")
    @PatchMapping
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(request);
    }
}
