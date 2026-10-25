package com.assesshub.repository;

import com.assesshub.domain.EmailNotification;
import com.assesshub.domain.enums.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    Page<EmailNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(EmailStatus status);
}
