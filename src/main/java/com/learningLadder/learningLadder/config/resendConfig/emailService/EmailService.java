package com.learningLadder.learningLadder.config.resendConfig.emailService;

import com.learningLadder.learningLadder.exception.emailDeliveryException.EmailDeliveryException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmailService {

    private final Resend resend;
    private final String from;

    public EmailService(Resend resend,
                        @Value("${app.integrations.resend.sender-email}") String senderEmail,
                        @Value("${app.integrations.resend.sender-name}") String senderName) {
        this.resend = resend;
        this.from = senderName + " <" + senderEmail + ">";
    }

    public void send(List<String> recipients, String subject, String html, String text) {
        CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                .from(from)
                .to(recipients)
                .subject(subject);

        if (html != null) builder.html(html);
        if (text != null) builder.text(text);

        try {
            CreateEmailResponse response = resend.emails().send(builder.build());
            log.info("Email sent [id={}, recipients={}]", response.getId(), recipients.size());
        } catch (ResendException ex) {
            log.error("Failed to send email [subject={}]: {}", subject, ex.getMessage());
            throw new EmailDeliveryException("Failed to send email", ex);
        }
    }
}