package com.skyroute.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends SkyRouteException {

    public NotFoundException(String resource, String id) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, resource + " '" + id + "' was not found");
    }
}
