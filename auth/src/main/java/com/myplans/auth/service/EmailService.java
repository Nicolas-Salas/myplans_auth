package com.myplans.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:no-reply@myplans.com}") String mailFrom,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom;
        this.frontendUrl = frontendUrl;
    }

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        if (!mailEnabled) {
            logger.info("==========================================================");
            logger.info("📧 SIMULACIÓN DE ENVÍO DE CORREO (app.mail.enabled=false) 📧");
            logger.info("Destinatario: {}", to);
            logger.info("Asunto: Recuperación de contraseña - MyPlans");
            logger.info("Enlace: {}", resetUrl);
            logger.info("==========================================================");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Recuperación de contraseña - MyPlans");
            helper.setText(buildHtmlBody(resetUrl), true);
            mailSender.send(message);
            logger.info("Correo de recuperación enviado a {}", to);
        } catch (MessagingException | MailException e) {
            // No relanzamos: el endpoint siempre responde 200 para no revelar si el correo existe.
            logger.error("Error enviando correo de recuperación a {}: {}", to, e.getMessage());
        }
    }

    private String buildHtmlBody(String resetUrl) {
        return """
            <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#1a2332">
              <h2 style="color:#2ecc71;margin:0 0 16px">MyPlans</h2>
              <p>Recibimos una solicitud para restablecer tu contraseña.</p>
              <p>Haz clic en el botón para crear una nueva. El enlace expira en <strong>15 minutos</strong>.</p>
              <p style="text-align:center;margin:28px 0">
                <a href="%s" style="background:#2ecc71;color:#1a2332;text-decoration:none;font-weight:bold;padding:12px 24px;border-radius:8px;display:inline-block">Restablecer contraseña</a>
              </p>
              <p style="font-size:12px;color:#888">Si no solicitaste este cambio, ignora este correo.</p>
              <p style="font-size:12px;color:#888;word-break:break-all">O copia este enlace en tu navegador: %s</p>
            </div>
            """.formatted(resetUrl, resetUrl);
    }
}
