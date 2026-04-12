package com.myplans.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = "http://localhost:3000/reset-password?token=" + token;
        
        logger.info("==========================================================");
        logger.info("📧 SIMULACIÓN DE ENVÍO DE CORREO 📧");
        logger.info("Destinatario: {}", to);
        logger.info("Asunto: Recuperación de contraseña - MyPlans");
        logger.info("Cuerpo: Para restablecer tu contraseña, haz clic en el siguiente enlace:");
        logger.info("{}", resetUrl);
        logger.info("==========================================================");
    }
}