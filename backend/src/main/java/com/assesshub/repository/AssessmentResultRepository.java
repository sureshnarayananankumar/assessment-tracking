package com.assesshub.repository;

import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.enums.ResultGradingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    Optional<AssessmentResult> findByAttemptId(Long attemptId);

    List<AssessmentResult> findByUserId(Long userId);

    @Query(value = """
            select r from AssessmentResult r join fetch r.user u join fetch r.assignment a join fetch r.attempt t
            where (:ownerId is null or a.owner.id = :ownerId)
              and (:assignmentId is null or a.id = :assignmentId)
              and (:passed is null or r.passed = :passed)
              and (:gradingStatus is null or r.gradingStatus = :gradingStatus)
              and (:flagged is null or r.flagged = :flagged)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search or lower(a.title) like :search)
            """,
            countQuery = """
            select count(r) from AssessmentResult r join r.user u join r.assignment a
            where (:ownerId is null or a.owner.id = :ownerId)
              and (:assignmentId is null or a.id = :assignmentId)
              and (:passed is null or r.passed = :passed)
              and (:gradingStatus is null or r.gradingStatus = :gradingStatus)
              and (:flagged is null or r.flagged = :flagged)
              and (:search is null or lower(u.email) like :search or lower(u.fullName) like :search or lower(a.title) like :search)
            """)
    Page<AssessmentResult> search(@Param("ownerId") Long ownerId,
                                  @Param("assignmentId") Long assignmentId,
                                  @Param("passed") Boolean passed,
                                  @Param("gradingStatus") ResultGradingStatus gradingStatus,
                                  @Param("flagged") Boolean flagged,
                                  @Param("search") String searchPattern,
                                  Pageable pageable);

    @Query("select r from AssessmentResult r join fetch r.user u join fetch r.attempt t where r.assignment.id = :assignmentId")
    List<AssessmentResult> findByAssignmentIdWithUser(@Param("assignmentId") Long assignmentId);

    @Query("""
            select coalesce(avg(r.percentage), 0),
                   coalesce(sum(case when r.passed = true then 1 else 0 end), 0),
                   coalesce(sum(case when r.passed = false then 1 else 0 end), 0),
                   coalesce(sum(case when r.gradingStatus = com.assesshub.domain.enums.ResultGradingStatus.PENDING_REVIEW then 1 else 0 end), 0),
                   count(r)
            from AssessmentResult r where (:ownerId is null or r.assignment.owner.id = :ownerId)
            """)
    List<Object[]> stats(@Param("ownerId") Long ownerId);

    @Query("select r.percentage from AssessmentResult r where r.gradingStatus = com.assesshub.domain.enums.ResultGradingStatus.GRADED and (:ownerId is null or r.assignment.owner.id = :ownerId)")
    List<BigDecimal> gradedPercentages(@Param("ownerId") Long ownerId);
}
