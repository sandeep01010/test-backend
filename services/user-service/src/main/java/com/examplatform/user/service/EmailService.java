package com.examplatform.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.mail-from:noreply@examplatform.com}")
    private String mailFrom;

    @Async
    public void sendInviteEmail(String toEmail, String firstName, String role, String tempPassword) {
        String loginUrl = frontendUrl + "/login";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#f8f9ff;padding:32px;border-radius:12px;">
              <div style="background:#6366f1;padding:24px;border-radius:8px;text-align:center;margin-bottom:24px;">
                <h1 style="color:white;margin:0;font-size:22px;">ExamPlatform</h1>
                <p style="color:rgba(255,255,255,0.85);margin:6px 0 0;font-size:14px;">Welcome aboard!</p>
              </div>
              <h2 style="color:#1e293b;">Hi %s,</h2>
              <p style="color:#475569;line-height:1.6;">
                You've been registered on <strong>ExamPlatform</strong> as a <strong>%s</strong>.
                Use the credentials below to log in and change your password immediately.
              </p>
              <div style="background:white;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:20px 0;">
                <table style="width:100%%;border-collapse:collapse;">
                  <tr><td style="padding:8px 0;color:#64748b;font-size:13px;">Login URL</td>
                      <td style="padding:8px 0;font-weight:600;"><a href="%s" style="color:#6366f1;">%s</a></td></tr>
                  <tr><td style="padding:8px 0;color:#64748b;font-size:13px;">Email</td>
                      <td style="padding:8px 0;font-weight:600;">%s</td></tr>
                  <tr><td style="padding:8px 0;color:#64748b;font-size:13px;">Temporary Password</td>
                      <td style="padding:8px 0;font-weight:600;font-family:monospace;background:#f1f5f9;padding:4px 8px;border-radius:4px;letter-spacing:2px;">%s</td></tr>
                </table>
              </div>
              <p style="color:#ef4444;font-size:13px;font-weight:600;">⚠ Please change your password after first login.</p>
              <a href="%s" style="display:inline-block;background:#6366f1;color:white;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin-top:8px;">
                Login Now →
              </a>
              <hr style="border:none;border-top:1px solid #e2e8f0;margin:28px 0;">
              <p style="color:#94a3b8;font-size:12px;">If you didn't expect this email, ignore it or contact your administrator.</p>
            </div>
            """.formatted(firstName, role, loginUrl, loginUrl, toEmail, tempPassword, loginUrl);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Your ExamPlatform account is ready");
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Invite email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send invite email to {}: {}", toEmail, e.getMessage());
        }
    }
}
