package com.assesshub.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a candidate writes to an attempt that is closed or past its deadline.
 * Transactions are configured with {@code noRollbackFor} this type so that the
 * server-side auto-submission performed just before throwing is still committed.
 */
public class AttemptClosedException extends ApiException {

    public AttemptClosedException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
