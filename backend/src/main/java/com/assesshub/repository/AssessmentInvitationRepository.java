package com.assesshub.repository;

import com.assesshub.domain.AssessmentInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AssessmentInvitationRepository extends JpaRepository<AssessmentInvitation, Long> {

    Optional<AssessmentInvitation> findByTokenHash(String tokenHash);

    Optional<AssessmentInvitation> findFirstByAssessmentAssignmentIdOrderByCreatedAtDescIdDesc(Long assessmentAssignmentId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AssessmentInvitation i set i.status = com.assesshub.domain.enums.InvitationStatus.REVOKED, i.revokedAt = :now
            where i.assessmentAssignment.id = :aaId
              and i.status in (com.assesshub.domain.enums.InvitationStatus.PENDING, com.assesshub.domain.enums.InvitationStatus.ACCEPTED)
            """)
    int revokeActiveForAssignee(@Param("aaId") Long assessmentAssignmentId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update AssessmentInvitation i set i.status = com.assesshub.domain.enums.InvitationStatus.EXPIRED
            where i.status in (com.assesshub.domain.enums.InvitationStatus.PENDING, com.assesshub.domain.enums.InvitationStatus.ACCEPTED)
              and i.expiresAt < :now
            """)
    int expireOverdue(@Param("now") Instant now);

    @Modifying
    @Query("delete from AssessmentInvitation i where i.assignment.id = :assignmentId")
    int deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
}
