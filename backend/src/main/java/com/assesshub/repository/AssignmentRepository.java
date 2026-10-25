package com.assesshub.repository;

import com.assesshub.domain.Assignment;
import com.assesshub.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("""
            select a from Assignment a
            where (:ownerId is null or a.owner.id = :ownerId)
              and (:status is null or a.status = :status)
              and (:skill is null or lower(a.skillCategory) = :skill)
              and (:search is null or lower(a.title) like :search)
            """)
    Page<Assignment> search(@Param("ownerId") Long ownerId,
                            @Param("status") AssignmentStatus status,
                            @Param("skill") String skillLower,
                            @Param("search") String searchPattern,
                            Pageable pageable);

    @Query("""
            select a from Assignment a
            where a.status = com.assesshub.domain.enums.AssignmentStatus.PUBLISHED
              and (a.startAt is null or a.startAt <= :now)
            """)
    List<Assignment> findReadyToActivate(@Param("now") Instant now);

    @Query("""
            select a from Assignment a
            where a.status in (com.assesshub.domain.enums.AssignmentStatus.PUBLISHED, com.assesshub.domain.enums.AssignmentStatus.ACTIVE)
              and a.endAt is not null and a.endAt < :now
            """)
    List<Assignment> findOpenPastEnd(@Param("now") Instant now);

    @Query("select a.status, count(a) from Assignment a where (:ownerId is null or a.owner.id = :ownerId) group by a.status")
    List<Object[]> countByStatus(@Param("ownerId") Long ownerId);

    @Query("select count(aq) > 0 from AssignmentQuestion aq where aq.question.id = :questionId")
    boolean isQuestionUsed(@Param("questionId") Long questionId);

    /** Rows of [assignmentId, questionCount]. */
    @Query("select aq.assignment.id, count(aq) from AssignmentQuestion aq where aq.assignment.id in :ids group by aq.assignment.id")
    List<Object[]> questionCounts(@Param("ids") Collection<Long> ids);
}
