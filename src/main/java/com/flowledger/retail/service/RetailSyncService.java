package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.domain.RetailEnums.SyncStatus;
import com.flowledger.retail.entity.RetailPosSyncOutbox;
import com.flowledger.retail.repository.RetailPosSyncOutboxRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RetailSyncService {
    private final RetailModuleGuard guard;
    private final RetailPosSyncOutboxRepository outbox;

    public RetailSyncService(RetailModuleGuard guard, RetailPosSyncOutboxRepository outbox) {
        this.guard = guard;
        this.outbox = outbox;
    }

    public SyncResponse accept(SyncRequest request) {
        UUID org = guard.ensureEnabled();
        Optional<RetailPosSyncOutbox> existing =
                outbox.findByOrganizationIdAndClientIdAndClientTxnId(org, request.clientId(), request.clientTxnId());

        if (existing.isPresent()) {
            RetailPosSyncOutbox e = existing.get();
            e.setPayloadJson(request.payloadJson());
            e.setStoreId(request.storeId());
            applyStatus(e, request.payloadJson());
            e = outbox.save(e);
            return new SyncResponse(
                    e.getId(),
                    e.getStatus() == SyncStatus.PROCESSED ? SyncStatus.DUPLICATE : e.getStatus(),
                    e.getStatus() == SyncStatus.PROCESSED ? "Already processed" : "Updated existing sync record");
        }

        RetailPosSyncOutbox e = new RetailPosSyncOutbox();
        e.setOrganizationId(org);
        e.setStoreId(request.storeId());
        e.setClientId(request.clientId());
        e.setClientTxnId(request.clientTxnId());
        e.setPayloadJson(request.payloadJson());
        applyStatus(e, request.payloadJson());
        e = outbox.save(e);
        return new SyncResponse(
                e.getId(),
                e.getStatus(),
                e.getStatus() == SyncStatus.PROCESSED ? "Checkout accepted" : "Queued for processing");
    }

    private void applyStatus(RetailPosSyncOutbox e, String payloadJson) {
        if (looksLikeCheckout(payloadJson)) {
            e.setStatus(SyncStatus.PROCESSED);
            e.setProcessedAt(OffsetDateTime.now());
            e.setErrorMessage(null);
        } else {
            e.setStatus(SyncStatus.PENDING);
            e.setProcessedAt(null);
        }
    }

    private boolean looksLikeCheckout(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return false;
        }
        String lower = payloadJson.toLowerCase(Locale.ROOT);
        return lower.contains("\"checkout\"")
                || lower.contains("\"payments\"")
                || lower.contains("checkout")
                || lower.contains("\"completed\"");
    }
}
