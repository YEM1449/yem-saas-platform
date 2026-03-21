package com.yem.hlm.backend.payments.domain;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotency guard for payment reminders.
 *
 * <p>Unique constraint {@code uk_sir_idempotency} on
 * {@code (schedule_item_id, reminder_type, channel, reminder_date)} prevents
 * the same reminder being sent twice for the same item on the same calendar day.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "schedule_item_reminder",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sir_idempotency",
                columnNames = {"schedule_item_id", "reminder_type", "channel", "reminder_date"}
        ),
        indexes = @Index(name = "idx_sir_tenant_date", columnList = "societe_id,reminder_date")
)
public class ScheduleItemReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Column(name = "schedule_item_id", nullable = false)
    private UUID scheduleItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private MessageChannel channel;

    /** Calendar date bucket used in the idempotency key. */
    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    public ScheduleItemReminder(UUID societeId, UUID scheduleItemId, ReminderType reminderType,
                                MessageChannel channel, LocalDate reminderDate) {
        this.societeId       = societeId;
        this.scheduleItemId  = scheduleItemId;
        this.reminderType    = reminderType;
        this.channel         = channel;
        this.reminderDate    = reminderDate;
        this.triggeredAt     = LocalDateTime.now();
    }
}
