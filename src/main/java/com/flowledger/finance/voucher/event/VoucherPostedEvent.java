package com.flowledger.finance.voucher.event;

import com.flowledger.platform.event.DomainEvent;
import java.util.UUID;

public class VoucherPostedEvent extends DomainEvent {
    private final UUID journalEntryId;

    public VoucherPostedEvent(Object source, UUID organizationId, UUID actorId, UUID voucherId, UUID journalEntryId) {
        super(source, organizationId, actorId, null, "VOUCHER", voucherId);
        this.journalEntryId = journalEntryId;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getVoucherId() {
        return getEntityId();
    }
}
