# Sales KPI Catalog — Implementation-Aligned
Date: 2026-03-05

## KPI Semantics (Locked)
- **Reservation**: deposit in `PENDING` or `CONFIRMED`
- **Sale**: contract in `SIGNED`
- **Sale amount / revenue**: `agreedPrice`
- **Discount analytics**: based on `listPrice - agreedPrice` when `listPrice` exists

## Global Filters
- Tenant: implicit from JWT
- Time range: `from` / `to`
- Optional dimensions: `projectId`, `agentId`
- AGENT callers: forced to own scope

## Commercial Dashboard KPIs (Current)
1. `salesCount`
- Number of signed contracts in period

2. `salesTotalAmount`
- Sum of `agreedPrice` for signed contracts in period

3. `avgSaleValue`
- `salesTotalAmount / salesCount` when count > 0

4. `depositsCount`
- Count of `CONFIRMED` deposits in period

5. `depositsTotalAmount`
- Sum of confirmed deposit amounts in period

6. `activeReservationsCount`
- Current active reservations (`PENDING` + `CONFIRMED`), not strictly period-bound

7. `conversionDepositToSaleRate`
- Dashboard-defined conversion metric from deposit to sale

8. `avgDaysDepositToSale`
- Average delay between reservation and signed sale when linkage exists

9. `avgDiscountPercent`
- Average discount percent where `listPrice` is available

10. `maxDiscountPercent`
- Maximum discount percent where `listPrice` is available

11. `salesByProject[]`
- Top project rows by sales amount/count

12. `salesByAgent[]`
- Top agent rows by sales amount/count

13. `discountByAgent[]`
- Top agent rows by average discount

14. `salesAmountByDay[]`
- Time-series for signed sales amount

## Receivables and Cash-Related KPI Extensions
Available in dedicated dashboards:
- receivables summary (`/api/dashboard/receivables`)
- cash dashboard (`/api/dashboard/commercial/cash`)

These extend the commercial KPI layer with collection and overdue visibility.

## Interpretation Notes
- A deposit is not a sale.
- Signed contracts are the source of truth for sales KPIs.
- Discount metrics should be interpreted only where `listPrice` is present.
