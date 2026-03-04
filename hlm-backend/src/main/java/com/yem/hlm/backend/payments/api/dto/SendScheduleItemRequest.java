package com.yem.hlm.backend.payments.api.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for issuing and sending a call-for-funds document.
 * If contactId is provided, recipient email/phone is derived from the contact record.
 * Otherwise, emailOverride / smsOverride are used directly.
 */
public record SendScheduleItemRequest(

        /** Contact to notify — required unless overrides are supplied. */
        UUID contactId,

        /** Override email address (used when contactId is null or contact has no email). */
        @Size(max = 160) String emailOverride,

        /** Override phone number (used when contactId is null or contact has no phone). */
        @Size(max = 30) String smsOverride,

        /** Whether to send by email. */
        boolean sendEmail,

        /** Whether to send by SMS. */
        boolean sendSms
) {}
