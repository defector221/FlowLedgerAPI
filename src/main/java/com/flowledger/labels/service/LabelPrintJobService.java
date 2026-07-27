package com.flowledger.labels.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.labels.dto.LabelDtos.PrintJobRequest;
import com.flowledger.labels.dto.LabelDtos.PrintJobResponse;
import com.flowledger.labels.dto.LabelDtos.RenderRequest;
import com.flowledger.labels.dto.LabelDtos.RenderResponse;
import com.flowledger.labels.dto.LabelDtos.TemplateFieldRequest;
import com.flowledger.labels.dto.LabelDtos.TemplateFieldResponse;
import com.flowledger.labels.dto.LabelDtos.TemplateRequest;
import com.flowledger.labels.dto.LabelDtos.TemplateResponse;
import com.flowledger.labels.entity.BarcodePrintJob;
import com.flowledger.labels.entity.LabelTemplateField;
import com.flowledger.labels.repository.BarcodePrintJobRepository;
import com.flowledger.labels.repository.LabelTemplateFieldRepository;
import com.flowledger.pdf.HtmlDocumentPdfRenderer;
import com.flowledger.retail.entity.RetailLabelTemplate;
import com.flowledger.retail.repository.RetailLabelTemplateRepository;
import com.flowledger.retail.service.RetailModuleGuard;
import com.flowledger.storage.BytesMultipartFile;
import com.flowledger.storage.StorageService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LabelPrintJobService {
    private final RetailModuleGuard guard;
    private final RetailLabelTemplateRepository templates;
    private final LabelTemplateFieldRepository fields;
    private final BarcodePrintJobRepository jobs;
    private final LabelPdfRenderer pdfRenderer;
    private final StorageService storage;

    public LabelPrintJobService(
            RetailModuleGuard guard,
            RetailLabelTemplateRepository templates,
            LabelTemplateFieldRepository fields,
            BarcodePrintJobRepository jobs,
            LabelPdfRenderer pdfRenderer,
            StorageService storage) {
        this.guard = guard;
        this.templates = templates;
        this.fields = fields;
        this.jobs = jobs;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates() {
        return templates.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        return toTemplateResponse(loadTemplate(id));
    }

    public TemplateResponse createTemplate(TemplateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (templates.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            throw conflict("Label template code already exists");
        }
        RetailLabelTemplate template = new RetailLabelTemplate();
        template.setOrganizationId(org());
        template.setCode(code);
        applyTemplate(template, request, true);
        RetailLabelTemplate saved = templates.save(template);
        saveFields(saved.getId(), request.fields());
        return toTemplateResponse(saved);
    }

    public TemplateResponse updateTemplate(UUID id, TemplateRequest request) {
        RetailLabelTemplate template = loadTemplate(id);
        applyTemplate(template, request, false);
        audit(template, false);
        RetailLabelTemplate saved = templates.save(template);
        if (request.fields() != null) {
            fields.deleteByTemplateId(saved.getId());
            saveFields(saved.getId(), request.fields());
        }
        return toTemplateResponse(saved);
    }

    public void deleteTemplate(UUID id) {
        RetailLabelTemplate template = loadTemplate(id);
        template.setDeleted(true);
        audit(template, false);
        templates.save(template);
    }

    @Transactional(readOnly = true)
    public RenderResponse render(RenderRequest request) {
        RetailLabelTemplate template = loadTemplate(request.templateId());
        byte[] pdf = pdfRenderer.render(template, request.values() == null ? Map.of() : request.values());
        return new RenderResponse(template.getId(), pdf, "application/pdf");
    }

    public PrintJobResponse queuePrint(PrintJobRequest request) {
        RetailLabelTemplate template = loadTemplate(request.templateId());
        BarcodePrintJob job = new BarcodePrintJob();
        job.setOrganizationId(org());
        job.setTemplateId(template.getId());
        job.setFilterJson(request.filterJson() == null ? Map.of() : request.filterJson());
        job.setCopies(request.copies() == null ? 1 : request.copies());
        job.setStatus("PENDING");
        TenantContext.userId().ifPresent(job::setCreatedBy);
        BarcodePrintJob saved = jobs.save(job);
        try {
            saved.setStatus("RUNNING");
            saved.setStartedAt(OffsetDateTime.now());
            byte[] pdf = pdfRenderer.render(template, Map.of());
            String key = "labels/" + org() + "/jobs/" + saved.getId() + ".pdf";
            storage.store(key, new BytesMultipartFile("labels.pdf", "application/pdf", pdf));
            saved.setOutputObjectKey(key);
            saved.setStatus("COMPLETED");
            saved.setCompletedAt(OffsetDateTime.now());
        } catch (Exception ex) {
            saved.setStatus("FAILED");
            saved.setErrorMessage(ex.getMessage());
            saved.setCompletedAt(OffsetDateTime.now());
        }
        return toJobResponse(jobs.save(saved));
    }

    @Transactional(readOnly = true)
    public List<PrintJobResponse> listJobs() {
        return jobs.findByOrganizationIdOrderByCreatedAtDesc(org()).stream()
                .map(this::toJobResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrintJobResponse getJob(UUID id) {
        return toJobResponse(loadJob(id));
    }

    private void applyTemplate(RetailLabelTemplate template, TemplateRequest request, boolean created) {
        template.setName(request.name());
        if (request.labelType() != null && !request.labelType().isBlank()) {
            template.setLabelType(request.labelType());
        }
        if (request.templateBody() != null) {
            template.setTemplateBody(request.templateBody());
        } else if (created && template.getTemplateBody() == null) {
            template.setTemplateBody("<html><body>{{productName}} {{barcode}}</body></html>");
        }
        if (request.canvasJson() != null) {
            template.setCanvasJson(request.canvasJson());
        }
        if (request.paperSize() != null) {
            template.setPaperSize(request.paperSize());
        }
        if (request.dpi() != null) {
            template.setDpi(request.dpi());
        }
        audit(template, created);
    }

    private void saveFields(UUID templateId, List<TemplateFieldRequest> fieldRequests) {
        if (fieldRequests == null) {
            return;
        }
        int index = 0;
        for (TemplateFieldRequest field : fieldRequests) {
            LabelTemplateField row = new LabelTemplateField();
            row.setTemplateId(templateId);
            row.setFieldType(field.fieldType());
            row.setX(field.x() == null ? BigDecimal.ZERO : field.x());
            row.setY(field.y() == null ? BigDecimal.ZERO : field.y());
            row.setWidth(field.width() == null ? BigDecimal.TEN : field.width());
            row.setHeight(field.height() == null ? new BigDecimal("5") : field.height());
            row.setFontSize(field.fontSize());
            row.setBindingKey(field.bindingKey());
            row.setZIndex(field.zIndex() == null ? index : field.zIndex());
            fields.save(row);
            index++;
        }
    }

    private RetailLabelTemplate loadTemplate(UUID id) {
        return templates
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Label template not found"));
    }

    private BarcodePrintJob loadJob(UUID id) {
        return jobs.findByIdAndOrganizationId(id, org()).orElseThrow(() -> notFound("Print job not found"));
    }

    private TemplateResponse toTemplateResponse(RetailLabelTemplate template) {
        List<TemplateFieldResponse> fieldResponses =
                fields.findByTemplateIdOrderByZIndexAsc(template.getId()).stream()
                        .map(f -> new TemplateFieldResponse(
                                f.getId(),
                                f.getFieldType(),
                                f.getX(),
                                f.getY(),
                                f.getWidth(),
                                f.getHeight(),
                                f.getFontSize(),
                                f.getBindingKey(),
                                f.getZIndex()))
                        .toList();
        return new TemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getLabelType(),
                template.getTemplateBody(),
                template.getCanvasJson(),
                template.getPaperSize(),
                template.getDpi(),
                fieldResponses,
                template.getVersion());
    }

    private PrintJobResponse toJobResponse(BarcodePrintJob job) {
        return new PrintJobResponse(
                job.getId(),
                job.getStatus(),
                job.getTemplateId(),
                job.getCopies(),
                job.getOutputObjectKey(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getErrorMessage());
    }

    private void audit(com.flowledger.common.entity.AuditedEntity entity, boolean created) {
        TenantContext.userId().ifPresent(user -> {
            if (created) {
                entity.setCreatedBy(user);
            }
            entity.setUpdatedBy(user);
        });
    }

    private UUID org() {
        return guard.ensureEnabled();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
