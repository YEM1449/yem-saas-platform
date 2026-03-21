package com.yem.hlm.backend.usermanagement.dto;

public record MembreFilter(
    String search,
    String role,
    Boolean actif
) {}
