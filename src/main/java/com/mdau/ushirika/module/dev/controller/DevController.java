package com.mdau.ushirika.module.dev.controller;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.WhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/superadmin/dev")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Developer Tools", description = "Developer testing utilities (SUPERADMIN only)")
public class DevController {

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    public record TestEmailRequest(
            @Email @NotBlank String to,
            String subject
    ) {}

    public record TestEmailResult(boolean sent, String message, String sentAt) {}

    @PostMapping("/test-email")
    @Operation(summary = "Send a test email to verify Brevo delivery is working")
    public ResponseEntity<ApiResponse<TestEmailResult>> testEmail(
            @Valid @RequestBody TestEmailRequest req
    ) {
        String subject = (req.subject() != null && !req.subject().isBlank())
                ? req.subject()
                : "Ushirika — Email delivery test";

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#1a1a1a;border:1px solid #e5e7eb;border-radius:8px;padding:32px">
                  <h2 style="color:#007834;margin-top:0">Email Delivery Test</h2>
                  <p>This is a test message sent from the Ushirika Welfare Organization developer tools panel.</p>
                  <table style="border-collapse:collapse;width:100%%;margin:20px 0;font-size:13px">
                    <tr>
                      <td style="padding:8px 12px;background:#f9fafb;font-weight:600;width:140px;border:1px solid #e5e7eb">Sent at</td>
                      <td style="padding:8px 12px;border:1px solid #e5e7eb;font-family:monospace">%s UTC</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 12px;background:#f9fafb;font-weight:600;border:1px solid #e5e7eb">Recipient</td>
                      <td style="padding:8px 12px;border:1px solid #e5e7eb;font-family:monospace">%s</td>
                    </tr>
                  </table>
                  <p style="color:#16a34a;font-weight:600">If you received this message, email delivery is working correctly.</p>
                  <p style="color:#6b7280;font-size:12px;margin-bottom:0">— Ushirika Welfare Organization Admin Panel</p>
                </div>
                """.formatted(now, req.to());

        try {
            emailService.sendPlain(req.to(), req.to(), subject, html);
            log.info("[dev/test-email] Test email sent to {}", req.to());
            return ResponseEntity.ok(ApiResponse.ok(
                    "Test email dispatched to " + req.to(),
                    new TestEmailResult(true, "Email queued successfully. Check the recipient inbox and the notification_logs table for delivery status.", now)
            ));
        } catch (Exception e) {
            log.error("[dev/test-email] Failed to send test email to {}: {}", req.to(), e.getMessage());
            throw new BadRequestException("Email dispatch failed: " + e.getMessage());
        }
    }

    public record TestWhatsAppRequest(
            @NotBlank String to,
            String templateName,
            List<String> params
    ) {}

    public record TestWhatsAppResult(boolean sent, String message, String sentAt) {}

    /** Defaults to Meta's built-in "hello_world" sample template (no params, always approved) so
     * this works even before any custom template has cleared review -- pass a real templateName
     * + matching params once one has been approved. */
    @PostMapping("/test-whatsapp")
    @Operation(summary = "Send a test WhatsApp template message to verify Cloud API delivery is working")
    public ResponseEntity<ApiResponse<TestWhatsAppResult>> testWhatsApp(
            @Valid @RequestBody TestWhatsAppRequest req
    ) {
        String templateName = (req.templateName() != null && !req.templateName().isBlank())
                ? req.templateName() : "hello_world";
        List<String> params = req.params() != null ? req.params() : List.of();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        whatsAppService.sendTemplate(req.to(), req.to(), templateName, params);
        log.info("[dev/test-whatsapp] Test WhatsApp queued to {} using template '{}'", req.to(), templateName);

        return ResponseEntity.ok(ApiResponse.ok(
                "Test WhatsApp message queued to " + req.to(),
                new TestWhatsAppResult(true,
                        "Message queued (sends async). Check WhatsApp on that phone and the notification_logs table for delivery status.",
                        now)
        ));
    }
}
