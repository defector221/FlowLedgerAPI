package com.flowledger.retail.domain;

public final class RetailEnums {
    private RetailEnums() {}

    public enum ShiftStatus {
        OPEN,
        CLOSED
    }

    public enum PosSaleStatus {
        DRAFT,
        HELD,
        COMPLETED,
        VOID
    }

    public enum ReceiptType {
        POS_RECEIPT,
        TAX_INVOICE,
        GIFT_RECEIPT,
        DUPLICATE
    }

    public enum PaymentMode {
        CASH,
        CARD,
        UPI,
        WALLET,
        CREDIT
    }

    public enum ReturnReason {
        DAMAGED,
        DEFECTIVE,
        WRONG_ITEM,
        SIZE_ISSUE,
        NOT_SATISFIED,
        EXCHANGE,
        OTHER
    }

    public enum RefundMode {
        REFUND,
        STORE_CREDIT,
        EXCHANGE,
        GIFT_CARD
    }

    public enum GiftCardStatus {
        ISSUED,
        ACTIVE,
        REDEEMED,
        EXPIRED,
        CANCELLED
    }

    public enum LocationType {
        SHELF,
        RACK,
        BIN,
        BACKROOM,
        DISPLAY,
        WAREHOUSE
    }

    public enum PromoType {
        PERCENT_OFF,
        AMOUNT_OFF,
        BUY_X_GET_Y,
        BILL_DISCOUNT,
        COUPON
    }

    public enum PriceType {
        RETAIL,
        WHOLESALE,
        MRP,
        SPECIAL,
        CLEARANCE
    }

    public enum CountType {
        CYCLE,
        FULL,
        SPOT
    }

    public enum SyncStatus {
        PENDING,
        PROCESSED,
        FAILED,
        DUPLICATE
    }
}
