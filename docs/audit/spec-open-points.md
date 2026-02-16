# Spec Open Points & Decision Register Seed

## Key open points
1. **Acompte vs Dépôt vs Réservation semantics**: legal/financial distinction and reporting impact.
2. **Temp Client**: is it a transient contact type or a state in prospect/client FSM?
3. **Tenant scope**: one legal company per tenant vs grouped entities.
4. **Property/Lot naming**: harmonize external business docs and API contracts.
5. **Administrative module depth**: explicit entities/workflows not fully specified.
6. **Document management baseline**: retention policy, storage provider, legal signatures.
7. **V2/V3 module acceptance criteria**: currently broad and non-testable in original docs.
8. **Integrations**: which providers and protocols are mandatory for phase-1/phase-2.

## Proposed decision labels
- `DECISION NEEDED` where business semantics are ambiguous.
- `MISSING` where no implementation artifact exists.
- `PARTIAL` where only subset of acceptance criteria is evidenced.
