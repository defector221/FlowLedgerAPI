package com.flowledger.platform.document.service;

import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.document.entity.DocumentAttachment;
import com.flowledger.platform.document.entity.DocumentComment;
import com.flowledger.platform.document.entity.DocumentTag;
import com.flowledger.platform.document.repository.DocumentAttachmentRepository;
import com.flowledger.platform.document.repository.DocumentCommentRepository;
import com.flowledger.platform.document.repository.DocumentTagRepository;
import com.flowledger.platform.history.service.DocumentHistoryService;
import com.flowledger.storage.StorageService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentCollaborationService {
    private final DocumentAttachmentRepository attachments;
    private final DocumentCommentRepository comments;
    private final DocumentTagRepository tags;
    private final StorageService storage;
    private final DocumentHistoryService history;

    public DocumentCollaborationService(
            DocumentAttachmentRepository attachments,
            DocumentCommentRepository comments,
            DocumentTagRepository tags,
            StorageService storage,
            DocumentHistoryService history) {
        this.attachments = attachments;
        this.comments = comments;
        this.tags = tags;
        this.storage = storage;
        this.history = history;
    }

    @Transactional
    public DocumentAttachment addAttachment(String entityType, UUID entityId, MultipartFile file) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId().orElse(null);
        String key = "org/" + org + "/docs/" + entityType + "/" + entityId + "/" + UUID.randomUUID() + "-"
                + file.getOriginalFilename();
        storage.store(key, file);
        DocumentAttachment attachment = new DocumentAttachment();
        attachment.setOrganizationId(org);
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageKey(key);
        attachment.setUploadedBy(user);
        attachment.setCreatedBy(user);
        attachment = attachments.save(attachment);
        history.record(entityType, entityId, "ATTACHMENT_ADDED", "Attached " + file.getOriginalFilename(), null);
        return attachment;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAttachments(String entityType, UUID entityId) {
        return attachments
                .findByOrganizationIdAndEntityTypeAndEntityIdAndDeletedAtIsNull(
                        TenantContext.getOrganizationId(), entityType, entityId)
                .stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "fileName", a.getFileName(),
                        "contentType", a.getContentType() == null ? "" : a.getContentType(),
                        "sizeBytes", a.getSizeBytes() == null ? 0L : a.getSizeBytes(),
                        "url", storage.getPresignedUrl(a.getStorageKey(), Duration.ofHours(1)),
                        "uploadedBy",
                                a.getUploadedBy() == null
                                        ? ""
                                        : a.getUploadedBy().toString(),
                        "createdAt", a.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public void deleteAttachment(UUID id) {
        DocumentAttachment attachment = attachments
                .findByIdAndOrganizationIdAndDeletedAtIsNull(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        attachment.setDeletedAt(OffsetDateTime.now());
        attachments.save(attachment);
        history.record(
                attachment.getEntityType(),
                attachment.getEntityId(),
                "ATTACHMENT_REMOVED",
                "Removed " + attachment.getFileName(),
                null);
    }

    @Transactional
    public DocumentComment addComment(String entityType, UUID entityId, String body, UUID parentId) {
        DocumentComment comment = new DocumentComment();
        comment.setOrganizationId(TenantContext.getOrganizationId());
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setBody(body);
        comment.setParentId(parentId);
        UUID author = TenantContext.userId().orElseThrow(() -> new IllegalStateException("User context is not set"));
        comment.setAuthorId(author);
        comment.setCreatedBy(author);
        comment = comments.save(comment);
        history.record(entityType, entityId, "COMMENT_ADDED", "Comment added", null);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<DocumentComment> listComments(String entityType, UUID entityId) {
        return comments.findByOrganizationIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                TenantContext.getOrganizationId(), entityType, entityId);
    }

    @Transactional
    public DocumentTag addTag(String entityType, UUID entityId, String tag) {
        UUID org = TenantContext.getOrganizationId();
        return tags.findByOrganizationIdAndEntityTypeAndEntityIdAndTag(org, entityType, entityId, tag)
                .orElseGet(() -> {
                    DocumentTag t = new DocumentTag();
                    t.setOrganizationId(org);
                    t.setEntityType(entityType);
                    t.setEntityId(entityId);
                    t.setTag(tag.trim());
                    t.setCreatedBy(TenantContext.userId().orElse(null));
                    t.setCreatedAt(OffsetDateTime.now());
                    DocumentTag saved = tags.save(t);
                    history.record(entityType, entityId, "TAG_ADDED", "Tagged " + tag, null);
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public List<DocumentTag> listTags(String entityType, UUID entityId) {
        return tags.findByOrganizationIdAndEntityTypeAndEntityId(
                TenantContext.getOrganizationId(), entityType, entityId);
    }

    @Transactional
    public void removeTag(String entityType, UUID entityId, String tag) {
        tags.deleteByOrganizationIdAndEntityTypeAndEntityIdAndTag(
                TenantContext.getOrganizationId(), entityType, entityId, tag);
        history.record(entityType, entityId, "TAG_REMOVED", "Removed tag " + tag, null);
    }
}
