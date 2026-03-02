package com.yem.hlm.backend.reminder;

import com.yem.hlm.backend.audit.repo.CommercialAuditRepository;
import com.yem.hlm.backend.contact.repo.ContactRepository;
import com.yem.hlm.backend.deposit.domain.Deposit;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.repo.DepositRepository;
import com.yem.hlm.backend.notification.repo.NotificationRepository;
import com.yem.hlm.backend.outbox.domain.OutboundMessage;
import com.yem.hlm.backend.outbox.repo.OutboundMessageRepository;
import com.yem.hlm.backend.payment.domain.PaymentCall;
import com.yem.hlm.backend.payment.repo.PaymentCallRepository;
import com.yem.hlm.backend.tenant.domain.Tenant;
import com.yem.hlm.backend.user.domain.User;
import com.yem.hlm.backend.user.domain.UserRole;
import com.yem.hlm.backend.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReminderService — verifies message and notification creation logic.
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock private ReminderProperties props;
    @Mock private DepositRepository depositRepository;
    @Mock private PaymentCallRepository paymentCallRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private OutboundMessageRepository messageRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommercialAuditRepository auditRepository;

    @InjectMocks
    private ReminderService service;

    private Tenant tenant;
    private User agent;

    @BeforeEach
    void setUp() {
        tenant = new Tenant("test-key", "Test Tenant");
        setId(tenant, UUID.randomUUID());

        agent = new User(tenant, "agent@test.com", "hash");
        agent.setRole(UserRole.ROLE_AGENT);
        setId(agent, UUID.randomUUID());

    }

    // =========================================================================
    // F2.2-A: Deposit due-date reminders
    // =========================================================================

    @Test
    void runDepositDueReminders_withPendingDeposit_queuesEmailMessage() {
        when(props.getDepositWarnDays()).thenReturn(List.of(7, 3, 1));
        Deposit deposit = mockDeposit(LocalDateTime.now().plusDays(7));

        when(depositRepository.findAllByStatusAndDueDateBetween(
                eq(DepositStatus.PENDING), any(), any()))
                .thenReturn(List.of(deposit))    // match for day 7
                .thenReturn(List.of())           // day 3
                .thenReturn(List.of());          // day 1

        when(messageRepository.existsPendingOrSent(deposit.getId(), "DEPOSIT_REMINDER"))
                .thenReturn(false);

        service.runDepositDueReminders();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(messageRepository, times(1)).save(captor.capture());

        OutboundMessage saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo("agent@test.com");
        assertThat(saved.getCorrelationType()).isEqualTo("DEPOSIT_REMINDER");
        assertThat(saved.getCorrelationId()).isEqualTo(deposit.getId());
    }

    @Test
    void runDepositDueReminders_whenAlreadySent_skipsDeposit() {
        when(props.getDepositWarnDays()).thenReturn(List.of(7, 3, 1));
        Deposit deposit = mockDeposit(LocalDateTime.now().plusDays(7));

        when(depositRepository.findAllByStatusAndDueDateBetween(
                eq(DepositStatus.PENDING), any(), any()))
                .thenReturn(List.of(deposit))
                .thenReturn(List.of())
                .thenReturn(List.of());

        when(messageRepository.existsPendingOrSent(deposit.getId(), "DEPOSIT_REMINDER"))
                .thenReturn(true);

        service.runDepositDueReminders();

        verify(messageRepository, never()).save(any());
    }

    // =========================================================================
    // F2.2-B: Payment call overdue notifications
    // =========================================================================

    @Test
    void runPaymentCallOverdueNotifications_createsEmailAndNotification() {
        UUID tenantId = tenant.getId();
        PaymentCall call = mockOverdueCall(tenantId);

        User admin = new User(tenant, "admin@test.com", "hash");
        admin.setRole(UserRole.ROLE_ADMIN);
        setId(admin, UUID.randomUUID());

        when(paymentCallRepository.findTenantsWithOverdueCalls()).thenReturn(List.of(tenantId));
        when(paymentCallRepository.findOverdueCallsWithAgent(tenantId)).thenReturn(List.of(call));
        when(userRepository.findByTenant_IdAndRoleInAndEnabledTrue(eq(tenantId), any()))
                .thenReturn(List.of(admin));
        when(messageRepository.existsPendingOrSent(call.getId(), "PAYMENT_CALL_OVERDUE"))
                .thenReturn(false);

        service.runPaymentCallOverdueNotifications();

        verify(messageRepository, times(1)).save(any(OutboundMessage.class));
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void runPaymentCallOverdueNotifications_whenAlreadySent_skipsCall() {
        UUID tenantId = tenant.getId();
        PaymentCall call = mockOverdueCall(tenantId);

        when(paymentCallRepository.findTenantsWithOverdueCalls()).thenReturn(List.of(tenantId));
        when(paymentCallRepository.findOverdueCallsWithAgent(tenantId)).thenReturn(List.of(call));
        when(userRepository.findByTenant_IdAndRoleInAndEnabledTrue(eq(tenantId), any()))
                .thenReturn(List.of());
        when(messageRepository.existsPendingOrSent(call.getId(), "PAYMENT_CALL_OVERDUE"))
                .thenReturn(true);

        service.runPaymentCallOverdueNotifications();

        verify(messageRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // F2.2-C: Prospect follow-up
    // =========================================================================

    @Test
    void runProspectFollowUp_withNoTenants_doesNothing() {
        when(props.getProspectStaleDays()).thenReturn(14);
        when(contactRepository.findAll()).thenReturn(List.of());

        service.runProspectFollowUp();

        verify(notificationRepository, never()).save(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Deposit mockDeposit(LocalDateTime dueDate) {
        Deposit deposit = mock(Deposit.class);
        UUID id = UUID.randomUUID();
        when(deposit.getId()).thenReturn(id);
        // lenient: not invoked when deposit is skipped (already-sent path)
        lenient().when(deposit.getAgent()).thenReturn(agent);
        lenient().when(deposit.getTenant()).thenReturn(tenant);
        lenient().when(deposit.getDueDate()).thenReturn(dueDate);
        return deposit;
    }

    private PaymentCall mockOverdueCall(UUID tenantId) {
        var contract = mock(com.yem.hlm.backend.contract.domain.SaleContract.class);
        lenient().when(contract.getAgent()).thenReturn(agent);

        var schedule = mock(com.yem.hlm.backend.payment.domain.PaymentSchedule.class);
        lenient().when(schedule.getSaleContract()).thenReturn(contract);

        var tranche = mock(com.yem.hlm.backend.payment.domain.PaymentTranche.class);
        lenient().when(tranche.getSchedule()).thenReturn(schedule);

        PaymentCall call = mock(PaymentCall.class);
        UUID callId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        // lenient: not invoked when call is skipped (already-sent path)
        lenient().when(call.getTenant()).thenReturn(tenant);
        lenient().when(call.getTranche()).thenReturn(tranche);
        lenient().when(call.getCallNumber()).thenReturn(1);
        return call;
    }

    /** Reflective helper to set an entity's UUID id field (no-arg constructor not available). */
    private static void setId(Object entity, UUID id) {
        try {
            var field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set id on " + entity.getClass().getSimpleName(), e);
        }
    }

    private static java.lang.reflect.Field findIdField(Class<?> clazz) {
        Class<?> cur = clazz;
        while (cur != null) {
            for (var f : cur.getDeclaredFields()) {
                if (f.getName().equals("id")) return f;
            }
            cur = cur.getSuperclass();
        }
        throw new IllegalArgumentException("No id field on " + clazz);
    }
}
