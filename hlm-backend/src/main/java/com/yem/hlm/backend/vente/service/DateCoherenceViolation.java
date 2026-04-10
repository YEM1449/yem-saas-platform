package com.yem.hlm.backend.vente.service;

/**
 * Describes a single date coherence rule violation.
 *
 * @param field       the field whose value caused the violation (e.g. "dateCompromis")
 * @param message     human-readable rule description
 * @param conflictWith the name of the field this field conflicts with
 */
public record DateCoherenceViolation(String field, String message, String conflictWith) {}
