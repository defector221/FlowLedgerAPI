package com.flowledger.finance.voucher.event;

import com.flowledger.platform.event.DomainEvent;
import java.util.UUID;

public class VoucherReversedEvent extends DomainEvent {
    private final UUID journalEntryId;
    private final UUID reversalJournalEntryId;

    public VoucherReversedEvent(
            Object source,
            UUID organizationId,
            UUID actorId,
            UUID voucherId,
            UUID journalEntryId,
            UUID reversalJournalEntryId) {
        super(source, organizationId, actorId, null, "VOUCHER", voucherId);
        this.journalEntryId = journalEntryId;
        this.reversalJournalEntryId = reversalJournalEntryId;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getReversalJournalEntryId() {
        return reversalJournalEntryId;
    }

    public UUID getVoucherId() {
        return getEntityId();
    }
}
