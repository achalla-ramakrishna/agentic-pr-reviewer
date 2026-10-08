package com.skyroute.common.error;

import org.springframework.http.HttpStatus;

public class SeatsUnavailableException extends SkyRouteException {

    public SeatsUnavailableException(String flightNumber, String cabin, int requested) {
        super("SEATS_UNAVAILABLE", HttpStatus.CONFLICT,
                "Flight " + flightNumber + " has fewer than " + requested + " seat(s) left in " + cabin);
    }
}
