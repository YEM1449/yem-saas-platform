package com.yem.hlm.backend.auth.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchSocieteRequest(@NotNull UUID societeId) {}
