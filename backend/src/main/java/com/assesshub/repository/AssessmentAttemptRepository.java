package com.assesshub.repository;

import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.enums.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Long> {

    Optional<AssessmentAttempt> findFirstByAssessmentAssignmentIdAndStatus(Long assessmentAssignmentId, AttemptStatus status);

    /** Serialises writes (autosave, activity, submit, auto-submit) on a single attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AssessmentAttempt t where t.id = :id")
    Optional<AssessmentAttempt> findByIdForUpdate(@Param("id") Long id);

    List<AssessmentAttempt> findByAssessmentAssignmentIdOrderByAttemptNumberDesc(Long assessmentAssignmentId);

    List<AssessmentAttempt> findByUserIdOrderByStartedAtDesc(Long userId);

    @Query("select t.id from AssessmentAttempt t where t.status = com.assesshub.domain.enums.AttemptStatus.IN_PROGRESS and t.deadlineAt < :now")
    List<Long> findInProgressPastDeadline(@Param("now") Instant now);

    @Query(value = """
            select t from AssessmentAttempt t join fetch t.user u join fetch t.assignment a
            where (:ownerId is null or a.owner.id = :ownerId)
              and (:assignmentId is null or a.id = :assignmentId)
              and (:status is null or t.status = :status)
              and (:flagged is null or t.flagged = :flagged)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search or lower(a.title) like :search)
            """,
            countQuery = """
            select count(t) from AssessmentAttempt t join t.user u join t.assignment a
            where (:ownerId is null or a.owner.id = :ownerId)
              and (:assignmentId is null or a.id = :assignmentId)
              and (:status is null or t.status = :status)
              and (:flagged is null or t.flagged = :flagged)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search or lower(a.title) like :search)
            """)
    Page<AssessmentAttempt> search(@Param("ownerId") Long ownerId,
                                   @Param("assignmentId") Long assignmentId,
                                   @Param("status") AttemptStatus status,
                                   @Param("flagged") Boolean flagged,
                                   @Param("search") String searchPattern,
                                   Pageable pageable);

    long countByAssignmentId(Long assignmentId);

    long countByAssignmentIdAndStatus(Long assignmentId, AttemptStatus status);

    @Query("select count(t) from AssessmentAttempt t where t.status = :status and (:ownerId is null or t.assignment.owner.id = :ownerId)")
    long countByStatusForOwner(@Param("status") AttemptStatus status, @Param("ownerId") Long ownerId);

    @Query("select count(t) from AssessmentAttempt t where t.flagged = true and (:ownerId is null or t.assignment.owner.id = :ownerId)")
    long countFlagged(@Param("ownerId") Long ownerId);

    @Query("select coalesce(sum(t.tabSwitchCount), 0), coalesce(sum(t.focusLossCount), 0), count(t) from AssessmentAttempt t where (:ownerId is null or t.assignment.owner.id = :ownerId)")
    List<Object[]> activityTotals(@Param("ownerId") Long ownerId);
}
