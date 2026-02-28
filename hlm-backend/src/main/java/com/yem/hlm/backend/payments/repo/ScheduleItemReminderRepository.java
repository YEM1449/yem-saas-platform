package com.yem.hlm.backend.payments.repo;

import com.yem.hlm.backend.outbox.domain.MessageChannel;
import com.yem.hlm.backend.payments.domain.ReminderType;
import com.yem.hlm.backend.payments.domain.ScheduleItemReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleItemReminderRepository extends JpaRepository<ScheduleItemReminder, UUID> {

    /**
     * Idempotency check — returns the existing reminder if the same type/channel/date
     * combination has already been sent for this schedule item.
     */
    Optional<ScheduleItemReminder> findByScheduleItemIdAndReminderTypeAndChannelAndReminderDate(
            UUID scheduleItemId,
            ReminderType reminderType,
            MessageChannel channel,
            LocalDate reminderDate);

    boolean existsByScheduleItemIdAndReminderTypeAndChannelAndReminderDate(
            UUID scheduleItemId,
            ReminderType reminderType,
            MessageChannel channel,
            LocalDate reminderDate);
}
