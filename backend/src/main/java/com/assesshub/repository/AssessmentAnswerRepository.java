package com.assesshub.repository;

import com.assesshub.domain.AssessmentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, Long> {

    @Query("select a from AssessmentAnswer a join fetch a.question q where a.attempt.id = :attemptId order by a.displayOrder asc")
    List<AssessmentAnswer> findByAttemptIdWithQuestion(@Param("attemptId") Long attemptId);

    Optional<AssessmentAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    boolean existsByQuestionId(Long questionId);

    @Query("select count(a) from AssessmentAnswer a where a.attempt.id = :attemptId and (a.selectedOptionIds is not null or a.textAnswer is not null or a.codeAnswer is not null or a.fileStoredName is not null)")
    long countAnswered(@Param("attemptId") Long attemptId);
}
