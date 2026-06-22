package com.examplatform.exam.exception;

import lombok.Getter;

/** Thrown when a student tries to attempt a locked exam without an active access grant
 *  covering its category (directly or via a group bundle). Mapped to HTTP 402 so the
 *  frontend can distinguish "needs payment" from other attempt failures and redirect
 *  into the payment flow instead of showing a generic error. */
@Getter
public class PaymentRequiredException extends RuntimeException {
    private final String scopeType;
    private final String scopeCode;
    private final long priceInPaise;

    public PaymentRequiredException(String scopeType, String scopeCode, long priceInPaise) {
        super("This exam requires access to " + scopeCode + " (" + scopeType + ")");
        this.scopeType = scopeType;
        this.scopeCode = scopeCode;
        this.priceInPaise = priceInPaise;
    }
}
