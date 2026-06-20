package com.yem.hlm.backend.visite.service;

/** Thrown when an agent already has an overlapping visite on the requested slot (RG-V05, 409). */
public class ConflitVisiteException extends RuntimeException {
    public ConflitVisiteException() {
        super("Vous avez déjà une visite sur ce créneau.");
    }
}
