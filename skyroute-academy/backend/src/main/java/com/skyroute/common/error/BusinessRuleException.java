package com.skyroute.common.error;

import org.springframework.http.HttpStatus;

/** A request that is well-formed but violates an airline business rule. */
public class BusinessRuleException extends SkyRouteException {

    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
