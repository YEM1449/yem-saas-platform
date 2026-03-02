package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;

/**
 * One aging bucket for the receivables aging analysis.
 * Age is based on days since the call's dueDate.
 *
 * <ul>
 *   <li>current    — dueDate >= today (not yet due)</li>
 *   <li>days30     — 0 &lt; days overdue &le; 30</li>
 *   <li>days60     — 30 &lt; days overdue &le; 60</li>
 *   <li>days90     — 60 &lt; days overdue &le; 90</li>
 *   <li>days90plus — days overdue &gt; 90</li>
 * </ul>
 */
public record AgingBucketDTO(long count, BigDecimal amount) {}
