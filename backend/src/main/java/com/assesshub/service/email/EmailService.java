package com.assesshub.service.email;

import com.assesshub.common.TextUtils;
import com.assesshub.config.AppProperties;
import com.assesshub.domain.EmailNotification;
import com.assesshub.domain.enums.EmailStatus;
import com.assesshub.repository.EmailNotificationRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * Delivers e-mail asynchronously through the configured SMTP server and records the outcome in
 * {@code email_notifications}. SMTP failures never break the business operation that triggered them.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailNotificationRepository repository;
    private final AppProperties props;
    private final Clock clock;

    public EmailService(JavaMailSender mailSender, EmailNotificationRepository repository,
                        AppProperties props, Clock clock) {
        this.mailSender = mailSender;
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequestedEvent event) {
        deliver(event);
    }

    public void deliver(EmailRequestedEvent event) {
        EmailNotification record = new EmailNotification();
        record.setRecipientEmail(event.to());
        record.setRecipientUserId(event.userId());
        record.setTemplate(event.template());
        record.setSubject(TextUtils.truncate(event.subject(), 255));
        record.setRelatedType(event.relatedType());
        record.setRelatedId(event.relatedId());

        if (!props.mail().enabled()) {
            record.setStatus(EmailStatus.SKIPPED);
            record.setErrorMessage("MAIL_ENABLED=false");
        } else {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(props.mail().from(), props.mail().fromName());
                helper.setTo(event.to());
                helper.setSubject(event.subject());
                helper.setText(event.text(), event.html());
                mailSender.send(message);
                record.setStatus(EmailStatus.SENT);
                record.setSentAt(clock.instant());
            } catch (Exception ex) {
                record.setStatus(EmailStatus.FAILED);
                record.setErrorMessage(TextUtils.truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 1000));
                log.warn("E-mail {} to {} failed: {}", event.template(), event.to(), ex.getMessage());
            }
        }
        if (props.mail().logLinks() && event.link() != null) {
            // Development aid only (MAIL_LOG_LINKS=true). Never enable in production.
            log.info("[DEV] {} e-mail for {} -> {}", event.template(), event.to(), event.link());
        }
        try {
            repository.save(record);
        } catch (RuntimeException ex) {
            log.warn("Could not record e-mail notification: {}", ex.getMessage());
        }
    }
}
