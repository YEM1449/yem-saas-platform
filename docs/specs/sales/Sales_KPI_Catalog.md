# Sales KPI Catalog (MVP + Phase 2)
Date: 2026-02-26

## Filters (global)
- tenant (implicit)
- dateRange: from/to (based on `signedAt` for sales KPIs, `createdAt/confirmedAt` for deposit KPIs)
- projectId (optional)
- agentId (optional)

## MVP KPIs (must be supported once Sales is implemented)
1) salesCount
- Definition: number of contracts with status SIGNED where signedAt in range
2) salesTotalAmount
- Definition: sum(agreedPrice) for signed contracts in range
3) avgSaleValue
- Definition: salesTotalAmount / salesCount
4) discountRate
- Definition: avg((listPrice - agreedPrice)/listPrice) where listPrice is present [OPEN POINT if listPrice absent]
5) salesByProject (top N)
- Definition: group signed contracts by projectId, sum agreedPrice, count
6) salesByAgent (top N)
- Definition: group by agentId, sum agreedPrice, count
7) conversionDepositToSale
- Definition: signedContracts / confirmedDeposits over same period (or cohort-based) [OPEN POINT: choose denominator in spec]
8) cycleTimeDepositToSaleDays
- Definition: avg(signedAt - deposit.confirmedAt) for contracts originating from deposits

## Phase 2 KPIs (requires payment schedule/payments)
- cashInTotal (sum payments in range)
- overdueAmount
- receivablesRemaining
- forecastRevenueByMonth
