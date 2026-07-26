package com.kenyarealestate.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final boolean configured;
    private final String fromAddress;

    public EmailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider,
                             @Value("${spring.mail.host:}") String mailHost,
                             @Value("${mail.from:no-reply@smartre.co.ke}") String fromAddress) {
        this.configured = StringUtils.hasText(mailHost);
        this.mailSender = configured ? mailSenderProvider.getIfAvailable() : null;
        this.fromAddress = fromAddress;
        if (!configured) {
            log.warn("spring.mail.host is not set - outgoing emails will be logged instead of sent. " +
                    "Set MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD to enable real delivery.");
        }
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!configured || mailSender == null) {
            log.info("EMAIL (not sent, no mail server configured) to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, e.getMessage(), e);
        }
    }
}
