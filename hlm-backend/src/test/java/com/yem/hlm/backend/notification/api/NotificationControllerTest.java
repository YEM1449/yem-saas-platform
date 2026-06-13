package com.yem.hlm.backend.notification.api;

import com.yem.hlm.backend.notification.api.dto.NotificationResponse;
import com.yem.hlm.backend.notification.domain.NotificationType;
import com.yem.hlm.backend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private NotificationResponse stub() {
        return new NotificationResponse(
                UUID.randomUUID(), NotificationType.DEPOSIT_CREATED,
                UUID.randomUUID(), "{}", false, LocalDateTime.now());
    }

    @Test
    void list_defaultSize_delegatesToService() throws Exception {
        when(notificationService.list(null, 50)).thenReturn(List.of(stub()));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(notificationService).list(null, 50);
    }

    @Test
    void list_explicitValidSize_accepted() throws Exception {
        when(notificationService.list(null, 200)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications").param("size", "200"))
                .andExpect(status().isOk());

        verify(notificationService).list(null, 200);
    }

    @Test
    void list_sizeOverMax_returns400() throws Exception {
        mockMvc.perform(get("/api/notifications").param("size", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/notifications").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_readFilter_passedToService() throws Exception {
        when(notificationService.list(true, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications").param("read", "true"))
                .andExpect(status().isOk());

        verify(notificationService).list(true, 50);
    }
}
