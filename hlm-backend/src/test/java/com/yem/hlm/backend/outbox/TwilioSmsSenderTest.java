package com.yem.hlm.backend.outbox;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import com.yem.hlm.backend.outbox.service.provider.TwilioSmsSender;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TwilioSmsSenderTest {

    private static final String ACCOUNT_SID = "ACtest1234567890";
    private static final String AUTH_TOKEN  = "test-auth-token";
    private static final String FROM        = "+15550000000";
    private static final String TO          = "+15551234567";
    private static final String BODY        = "Test SMS message";

    @Test
    void send_calls_twilio_message_creator_with_correct_params() {
        try (MockedStatic<Twilio> twilioMock = mockStatic(Twilio.class);
             MockedStatic<Message> messageMock = mockStatic(Message.class)) {

            // Stub Twilio.init to do nothing
            twilioMock.when(() -> Twilio.init(anyString(), anyString())).thenAnswer(inv -> null);

            // Stub Message.creator to return a mock creator
            MessageCreator mockCreator = mock(MessageCreator.class);
            Message mockMessage = mock(Message.class);
            when(mockCreator.create()).thenReturn(mockMessage);
            messageMock.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            TwilioSmsSender sender = new TwilioSmsSender(ACCOUNT_SID, AUTH_TOKEN, FROM);
            sender.send(TO, BODY);

            // Verify Message.creator was called with correct to/from/body
            messageMock.verify(() -> Message.creator(
                    new PhoneNumber(TO),
                    new PhoneNumber(FROM),
                    BODY
            ));
            verify(mockCreator).create();
        }
    }

    @Test
    void send_throws_runtime_exception_when_twilio_fails() {
        try (MockedStatic<Twilio> twilioMock = mockStatic(Twilio.class);
             MockedStatic<Message> messageMock = mockStatic(Message.class)) {

            twilioMock.when(() -> Twilio.init(anyString(), anyString())).thenAnswer(inv -> null);

            MessageCreator mockCreator = mock(MessageCreator.class);
            when(mockCreator.create()).thenThrow(new ApiException("Twilio API error", 21211, null, 400, null));
            messageMock.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            TwilioSmsSender sender = new TwilioSmsSender(ACCOUNT_SID, AUTH_TOKEN, FROM);

            assertThatThrownBy(() -> sender.send(TO, BODY))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageStartingWith("Twilio send failed:");
        }
    }
}
