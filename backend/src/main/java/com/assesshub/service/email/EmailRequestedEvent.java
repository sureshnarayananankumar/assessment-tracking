package com.assesshub.service.email;

import com.assesshub.domain.enums.EmailTemplate;

/**
 * Published inside a transaction; delivered asynchronously after commit so an e-mail is
 * never sent for a change that was rolled back. {@code link} is used only for optional
 * development logging and is never persisted.
 */
public record EmailRequestedEvent(
        String to,
        Long userId,
        EmailTemplate template,
        String subject,
        String html,
        String text,
        String relatedType,
        Long relatedId,
        String link) {
}
