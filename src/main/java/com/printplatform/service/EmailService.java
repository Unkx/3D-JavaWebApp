package com.printplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, UUID token) {
        String link = baseUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Reset hasła — Druk3D");
        message.setText(
            "Cześć,\n\n" +
            "Otrzymaliśmy prośbę o reset hasła dla konta " + to + " w serwisie Druk3D.\n\n" +
            "Kliknij poniższy link, aby ustawić nowe hasło (ważny przez 1 godzinę):\n\n" +
            link + "\n\n" +
            "Jeśli to nie Ty wysłałeś/-aś tę prośbę, zignoruj tę wiadomość.\n\n" +
            "— Zespół Druk3D"
        );
        mailSender.send(message);
    }
}
