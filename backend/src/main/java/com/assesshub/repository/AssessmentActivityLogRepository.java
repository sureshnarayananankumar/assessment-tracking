package com.assesshub.repository;

import com.assesshub.domain.AssessmentActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentActivityLogRepository extends JpaRepository<AssessmentActivityLog, Long> {

    List<AssessmentActivityLog> findByAttemptIdOrderByServerTimestampAscIdAsc(Long attemptId);

    long countByAttemptId(Long attemptId);
}
