package com.flowledger.retail.exception;

import com.flowledger.retail.dto.RetailDtos.PosCheckoutConflictResponse;

public class PosCheckoutConflictException extends RuntimeException {
    private final PosCheckoutConflictResponse body;

    public PosCheckoutConflictException(PosCheckoutConflictResponse body) {
        super(body.message());
        this.body = body;
    }

    public PosCheckoutConflictResponse body() {
        return body;
    }
}
