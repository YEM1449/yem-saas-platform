package com.yem.hlm.backend.vente.domain;

/**
 * Lifecycle of the sale contract document within a Vente.
 *
 * <ul>
 *   <li>{@link #PENDING} — not yet generated; "Signer" button is disabled.</li>
 *   <li>{@link #GENERATED} — PDF generated and attached; "Signer" button enabled.</li>
 *   <li>{@link #SIGNED} — contract signed by all parties.</li>
 * </ul>
 */
public enum ContractStatus {
    PENDING,
    GENERATED,
    SIGNED
}
