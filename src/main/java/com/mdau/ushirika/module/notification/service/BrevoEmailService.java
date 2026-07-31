package com.mdau.ushirika.module.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.module.notification.entity.NotificationLog;
import com.mdau.ushirika.module.notification.enums.NotificationChannel;
import com.mdau.ushirika.module.notification.enums.NotificationStatus;
import com.mdau.ushirika.module.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BrevoEmailService implements EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.brevo.api-key:NOT_SET}")
    private String apiKey;

    @Value("${app.brevo.sender-email:noreply@ushirikawelfare.org}")
    private String senderEmail;

    @Value("${app.brevo.sender-name:Ushirika Welfare}")
    private String senderName;

    @Value("${spring.mail.password:NOT_SET}")
    private String smtpPassword;

    public BrevoEmailService(JavaMailSender mailSender,
                             NotificationLogRepository logRepository,
                             ObjectMapper objectMapper) {
        this.mailSender    = mailSender;
        this.logRepository = logRepository;
        this.objectMapper  = objectMapper;
        this.httpClient    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Async
    @Override
    public void sendEmailVerificationOtp(String toEmail, String name, String otp) {
        String subject = "Your Ushirika Welfare Verification Code";
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#1A4731">Verify Your Email</h2>
                  <p>Hi %s,</p>
                  <p>Use the code below to verify your Ushirika Welfare account. It expires in <strong>15 minutes</strong>.</p>
                  <div style="font-size:36px;font-weight:700;letter-spacing:8px;color:#1A4731;margin:24px 0">%s</div>
                  <p style="color:#888;font-size:13px">If you did not register, ignore this email.</p>
                </div>
                """.formatted(name, otp);
        sendPlain(toEmail, name, subject, html);
    }

    @Async
    @Override
    public void sendPasswordResetOtp(String toEmail, String name, String otp) {
        String subject = "Reset Your Ushirika Welfare Password";
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#1A4731">Password Reset</h2>
                  <p>Hi %s,</p>
                  <p>Use this code to reset your password. It expires in <strong>15 minutes</strong>.</p>
                  <div style="font-size:36px;font-weight:700;letter-spacing:8px;color:#1A4731;margin:24px 0">%s</div>
                  <p style="color:#888;font-size:13px">If you did not request this, ignore this email.</p>
                </div>
                """.formatted(name, otp);
        sendPlain(toEmail, name, subject, html);
    }

    @Async
    @Override
    public void sendWelcome(String toEmail, String name, String memberId) {
        String subject = "Welcome to Ushirika Welfare!";
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:auto">
                  <h2 style="color:#1A4731">Welcome, %s!</h2>
                  <p>Your membership has been approved. Your Member ID is:</p>
                  <div style="font-size:24px;font-weight:700;color:#1A4731;margin:16px 0">%s</div>
                  <p>Log in to your member portal to view your dashboard, make contributions, and apply for welfare.</p>
                  <p>— Ushirika Welfare Team</p>
                </div>
                """.formatted(name, memberId);
        sendPlain(toEmail, name, subject, html);
    }

    @Async
    @Override
    public void sendFormSentCredentials(String toEmail, String name, String tempPassword, String onboardingUrl) {
        String subject = "Your Ushirika Welfare Application Has Been Accepted — Next Steps";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#1a1a1a">
                  <h2 style="color:#1A4731">Good news, %s!</h2>
                  <p>Your membership application has been accepted in principle. To become a full member, please
                     log in and complete a short onboarding process: set your password, upload some additional
                     information, review our bylaws, and pay your registration fee.</p>
                  <table style="border-collapse:collapse;width:100%%;margin:24px 0;border:1px solid #e5e7eb">
                    <tr>
                      <td style="padding:12px 16px;font-weight:600;width:160px">Login Email</td>
                      <td style="padding:12px 16px">%s</td>
                    </tr>
                    <tr style="background:#f9fafb">
                      <td style="padding:12px 16px;font-weight:600;border-top:1px solid #e5e7eb">Temporary Password</td>
                      <td style="padding:12px 16px;border-top:1px solid #e5e7eb;font-family:monospace;font-weight:700;font-size:16px">%s</td>
                    </tr>
                  </table>
                  <p><a href="%s" style="display:inline-block;padding:12px 24px;background:#1a6b3c;color:#fff;text-decoration:none;border-radius:24px;font-weight:600">Continue Your Application</a></p>
                  <p style="color:#666;font-size:13px">You will be asked to set a new password on your first login. Questions? Contact
                     <a href="mailto:info@ushirikacommunity.site">info@ushirikacommunity.site</a></p>
                </div>
                """.formatted(name, toEmail, tempPassword, onboardingUrl);
        sendPlain(toEmail, name, subject, html);
    }

    @Async
    @Override
    public void sendMembershipApproved(String toEmail, String name, String memberId) {
        String subject = "You Are a Member Now! — Ushirika Welfare Foundation";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#1a1a1a">
                  <h2 style="color:#1A4731">Congratulations, %s — you are a member now!</h2>
                  <p>Your registration fee has been verified and your membership is fully approved. You can now
                     log in with the same credentials to access the full member portal.</p>
                  <p><strong>Your Member ID: %s</strong></p>
                  <p>— Ushirika Welfare Foundation</p>
                </div>
                """.formatted(name, memberId);
        sendPlain(toEmail, name, subject, html);
    }

    @Async
    @Override
    public void sendPlain(String toEmail, String toName, String subject, String htmlBody) {
        NotificationLog logEntry = logRepository.save(
                NotificationLog.builder()
                        .channel(NotificationChannel.EMAIL)
                        .recipient(toEmail)
                        .recipientName(toName)
                        .subject(subject)
                        .body(truncate(htmlBody, 2000))
                        .status(NotificationStatus.PENDING)
                        .build()
        );

        // Primary: Brevo REST API over HTTPS (port 443 — never blocked by Railway)
        if (!"NOT_SET".equals(apiKey)) {
            try {
                sendViaBrevoApi(toEmail, toName, subject, htmlBody);
                log.info("Email sent via Brevo API to {}", toEmail);
                logEntry.setStatus(NotificationStatus.SENT);
                logRepository.save(logEntry);
                return;
            } catch (Exception e) {
                log.warn("Brevo API failed for {}, trying SMTP fallback: {}", toEmail, e.getMessage());
            }
        }

        // Fallback: SMTP relay
        if ("NOT_SET".equals(smtpPassword)) {
            log.warn("[DEV EMAIL — not sent] To: {} | Subject: {}", toEmail, subject);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
            return;
        }

        try {
            sendViaSMTP(toEmail, toName, subject, htmlBody);
            log.info("Email sent via Brevo SMTP to {}", toEmail);
            logEntry.setStatus(NotificationStatus.SENT);
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("All email methods failed for {}: {}", toEmail, e.getMessage());
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(truncate(e.getMessage(), 500));
            logRepository.save(logEntry);
        }
    }

    private void sendViaBrevoApi(String toEmail, String toName, String subject, String htmlBody) throws Exception {
        Map<String, Object> payload = Map.of(
                "sender",      Map.of("email", senderEmail, "name", senderName),
                "to",          List.of(Map.of("email", toEmail, "name", toName != null ? toName : toEmail)),
                "subject",     subject,
                "htmlContent", htmlBody
        );

        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Brevo API returned " + response.statusCode() + ": " + response.body());
        }
    }

    private void sendViaSMTP(String toEmail, String toName, String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(senderEmail, senderName);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
