package com.assesshub.domain;

import com.assesshub.domain.enums.EmailStatus;
import com.assesshub.domain.enums.EmailTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Delivery log for outgoing e-mail. Bodies and links are intentionally not stored.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_notifications")
public class EmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private EmailTemplate template;

    @Column(nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private EmailStatus status = EmailStatus.PENDING;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
