package com.assesshub.repository;

import com.assesshub.domain.AssignmentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, Long> {

    List<AssignmentAttachment> findByAssignmentIdOrderByCreatedAtAsc(Long assignmentId);

    Optional<AssignmentAttachment> findByIdAndAssignmentId(Long id, Long assignmentId);
}
