package com.assesshub.repository;

import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.enums.AssigneeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentAssignmentRepository extends JpaRepository<AssessmentAssignment, Long> {

    Optional<AssessmentAssignment> findByAssignmentIdAndUserId(Long assignmentId, Long userId);

    /** Row lock used when starting attempts so concurrent "start" clicks cannot create duplicates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select aa from AssessmentAssignment aa where aa.assignment.id = :assignmentId and aa.user.id = :userId")
    Optional<AssessmentAssignment> findForUpdate(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    Optional<AssessmentAssignment> findByIdAndAssignmentId(Long id, Long assignmentId);

    @Query(value = """
            select aa from AssessmentAssignment aa join fetch aa.user u
            where aa.assignment.id = :assignmentId
              and (:status is null or aa.status = :status)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search)
            """,
            countQuery = """
            select count(aa) from AssessmentAssignment aa join aa.user u
            where aa.assignment.id = :assignmentId
              and (:status is null or aa.status = :status)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search)
            """)
    Page<AssessmentAssignment> searchByAssignment(@Param("assignmentId") Long assignmentId,
                                                  @Param("status") AssigneeStatus status,
                                                  @Param("search") String searchPattern,
                                                  Pageable pageable);

    @Query("""
            select aa from AssessmentAssignment aa join fetch aa.assignment a
            where aa.user.id = :userId
              and aa.status <> com.assesshub.domain.enums.AssigneeStatus.REVOKED
              and a.status in (com.assesshub.domain.enums.AssignmentStatus.PUBLISHED,
                               com.assesshub.domain.enums.AssignmentStatus.ACTIVE,
                               com.assesshub.domain.enums.AssignmentStatus.COMPLETED)
            order by aa.assignedAt desc
            """)
    List<AssessmentAssignment> findVisibleForUser(@Param("userId") Long userId);

    long countByAssignmentId(Long assignmentId);

    long countByAssignmentIdAndStatus(Long assignmentId, AssigneeStatus status);

    @Query("select aa.status, count(aa) from AssessmentAssignment aa where (:ownerId is null or aa.assignment.owner.id = :ownerId) group by aa.status")
    List<Object[]> countByStatus(@Param("ownerId") Long ownerId);

    @Query("select count(distinct aa.user.id) from AssessmentAssignment aa where (:ownerId is null or aa.assignment.owner.id = :ownerId)")
    long countDistinctInvitedUsers(@Param("ownerId") Long ownerId);

    @Query("""
            select aa from AssessmentAssignment aa join fetch aa.assignment a
            where aa.status = com.assesshub.domain.enums.AssigneeStatus.ASSIGNED
              and ((aa.dueAt is not null and aa.dueAt < :now)
                   or (aa.dueAt is null and a.endAt is not null and a.endAt < :now))
            """)
    List<AssessmentAssignment> findAssignedPastDeadline(@Param("now") Instant now);

    @Query("""
            select aa from AssessmentAssignment aa join fetch aa.assignment a join fetch aa.user u
            where aa.status = com.assesshub.domain.enums.AssigneeStatus.ASSIGNED
              and aa.reminderCount = 0
              and a.status in (com.assesshub.domain.enums.AssignmentStatus.PUBLISHED, com.assesshub.domain.enums.AssignmentStatus.ACTIVE)
              and coalesce(aa.dueAt, a.endAt) is not null
              and coalesce(aa.dueAt, a.endAt) > :now
              and coalesce(aa.dueAt, a.endAt) <= :until
            """)
    List<AssessmentAssignment> findDueForReminder(@Param("now") Instant now, @Param("until") Instant until);

    @Query("select count(aa) > 0 from AssessmentAssignment aa where aa.assignment.id = :assignmentId and aa.dueAt is not null and aa.dueAt > :now")
    boolean hasExtendedDeadlines(@Param("assignmentId") Long assignmentId, @Param("now") Instant now);

    /** Rows of [assignmentId, assigneeCount, completedCount]. */
    @Query("""
            select aa.assignment.id, count(aa),
                   sum(case when aa.status = com.assesshub.domain.enums.AssigneeStatus.COMPLETED then 1 else 0 end)
            from AssessmentAssignment aa where aa.assignment.id in :ids group by aa.assignment.id
            """)
    List<Object[]> countsByAssignmentIds(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from AssessmentAssignment aa where aa.assignment.id = :assignmentId")
    int deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
}
