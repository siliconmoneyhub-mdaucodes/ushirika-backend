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
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WhatsApp Business Platform (Meta Cloud API) integration. Only sends pre-approved message
 * templates -- WhatsApp does not permit business-initiated free-form text outside an active
 * 24-hour customer-service window, so unlike AfricasTalkingService/BrevoEmailService there is no
 * plain-text send path here.
 */
@Slf4j
@Service
public class WhatsAppCloudApiService implements WhatsAppService {

    private static final String API_VERSION = "v22.0";

    private final RestClient restClient;
    private final NotificationLogRepository logRepository;
    private final String phoneNumberId;
    private final boolean devMode;

    public WhatsAppCloudApiService(
            RestClient.Builder restClientBuilder,
            NotificationLogRepository logRepository,
            @Value("${app.whatsapp.access-token:NOT_SET}") String accessToken,
            @Value("${app.whatsapp.phone-number-id:NOT_SET}") String phoneNumberId
    ) {
        this.logRepository = logRepository;
        this.phoneNumberId = phoneNumberId;
        this.devMode = "NOT_SET".equals(accessToken) || "NOT_SET".equals(phoneNumberId);

        this.restClient = restClientBuilder
                .baseUrl("https://graph.facebook.com/" + API_VERSION)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    @Async
    @Override
    public void sendTemplate(String phone, String recipientName, String templateName, List<String> bodyParams) {
        String to = phone == null ? null : phone.replaceAll("[^0-9]", "");

        NotificationLog logEntry = logRepository.save(
                NotificationLog.builder()
                        .channel(NotificationChannel.WHATSAPP)
                        .recipient(phone)
                        .recipientName(recipientName)
                        .body(truncate(templateName + " " + bodyParams, 500))
                        .status(NotificationStatus.PENDING)
                        .build()
        );

        if (devMode) {
            log.warn("[DEV WHATSAPP — not sent] To: {} | Template: {} | Params: {}", phone, templateName, bodyParams);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
            return;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", "en_US"),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", bodyParams.stream()
                                        .map(p -> Map.of("type", "text", "text", p))
                                        .collect(Collectors.toList())
                        ))
                )
        );

        try {
            String response = restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("WhatsApp template '{}' sent to {}: {}", templateName, phone, response);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("WhatsApp send failed for {}: {}", phone, e.getMessage());
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
