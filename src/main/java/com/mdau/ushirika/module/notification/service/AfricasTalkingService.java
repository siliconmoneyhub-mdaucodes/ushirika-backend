package com.mdau.ushirika.module.notification.service;

import com.mdau.ushirika.module.notification.entity.NotificationLog;
import com.mdau.ushirika.module.notification.enums.NotificationChannel;
import com.mdau.ushirika.module.notification.enums.NotificationStatus;
import com.mdau.ushirika.module.notification.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class AfricasTalkingService implements SmsService {

    private final RestClient restClient;
    private final NotificationLogRepository logRepository;
    private final String username;
    private final String senderId;
    private final boolean devMode;

    public AfricasTalkingService(
            RestClient.Builder restClientBuilder,
            NotificationLogRepository logRepository,
            @Value("${app.africas-talking.api-key:NOT_SET}") String apiKey,
            @Value("${app.africas-talking.username:NOT_SET}") String username,
            @Value("${app.africas-talking.sender-id:USHIRIKA}") String senderId
    ) {
        this.logRepository = logRepository;
        this.username  = username;
        this.senderId  = senderId;
        this.devMode   = "NOT_SET".equals(apiKey) || "NOT_SET".equals(username);

        this.restClient = restClientBuilder
                .baseUrl("https://api.africastalking.com/version1")
                .defaultHeader("apiKey", apiKey)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Async
    @Override
    public void send(String phone, String recipientName, String message) {
        NotificationLog logEntry = logRepository.save(
                NotificationLog.builder()
                        .channel(NotificationChannel.SMS)
                        .recipient(phone)
                        .recipientName(recipientName)
                        .body(truncate(message, 160))
                        .status(NotificationStatus.PENDING)
                        .build()
        );

        if (devMode) {
            log.warn("[DEV SMS — not sent] To: {} | Message: {}", phone, message);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
            return;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", phone);
        form.add("message", message);
        form.add("from", senderId);

        try {
            String response = restClient.post()
                    .uri("/messaging")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            log.info("SMS sent to {}: {}", phone, response);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("SMS failed for {}: {}", phone, e.getMessage());
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(truncate(e.getMessage(), 500));
            logRepository.save(logEntry);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
