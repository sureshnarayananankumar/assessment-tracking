package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.RequestUtils;
import com.assesshub.common.TextUtils;
import com.assesshub.domain.AuditLog;
import com.assesshub.dto.MiscDtos.AuditLogDto;
import com.assesshub.repository.AuditLogRepository;
import com.assesshub.security.CurrentUser;
import com.assesshub.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records security-relevant and administrative actions. Joins the caller's transaction so an
 * action and its audit entry are committed (or rolled back) together.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(String action, String entityType, Object entityId, String details) {
        UserPrincipal actor = CurrentUser.find().orElse(null);
        logAs(actor == null ? null : actor.getId(), actor == null ? null : actor.getEmail(),
                action, entityType, entityId, details);
    }

    @Transactional
    public void logAs(Long actorId, String actorEmail, String action, String entityType, Object entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorEmail(TextUtils.truncate(actorEmail, 255));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : TextUtils.truncate(String.valueOf(entityId), 64));
        entry.setDetails(TextUtils.truncate(details, 4000));
        entry.setIpAddress(RequestUtils.clientIp());
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(String action, String entityType, String search,
                                            Instant from, Instant to, Pageable pageable) {
        return PageResponse.from(
                repository.search(TextUtils.trimToNull(action), TextUtils.trimToNull(entityType),
                        TextUtils.likePattern(search), from, to, pageable),
                l -> new AuditLogDto(l.getId(), l.getActorId(), l.getActorEmail(), l.getAction(), l.getEntityType(),
                        l.getEntityId(), l.getDetails(), l.getIpAddress(), l.getCreatedAt()));
    }
}
