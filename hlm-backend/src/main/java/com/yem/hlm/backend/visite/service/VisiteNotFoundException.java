package com.yem.hlm.backend.visite.service;

import java.util.UUID;

/** Thrown when a visite does not exist in the current société (404). */
public class VisiteNotFoundException extends RuntimeException {
    public VisiteNotFoundException(UUID id) {
        super("Visite introuvable : " + id);
    }
}
