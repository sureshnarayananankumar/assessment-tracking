package com.assesshub.domain;

import com.assesshub.domain.enums.ActivityEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_activity_logs")
public class AssessmentActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssessmentAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, columnDefinition = "varchar(30)")
    private ActivityEventType eventType;

    /** Browser-reported time (informational, clamped to the attempt window). */
    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

    @Column(name = "server_timestamp", nullable = false)
    private Instant serverTimestamp;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 500)
    private String details;

    @Column(name = "counted_as_violation", nullable = false)
    private boolean countedAsViolation;
}
