package com.assesshub.repository;

import com.assesshub.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select l from AuditLog l
            where (:action is null or l.action = :action)
              and (:entityType is null or l.entityType = :entityType)
              and (:search is null or lower(l.actorEmail) like :search or l.entityId like :search)
              and (:from is null or l.createdAt >= :from)
              and (:to is null or l.createdAt <= :to)
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("search") String searchPattern,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
