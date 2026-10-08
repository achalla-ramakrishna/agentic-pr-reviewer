package com.skyroute.flight;

public enum FlightStatus {
    SCHEDULED, DELAYED, BOARDING, DEPARTED, ARRIVED, CANCELLED;

    /** Seats can only be sold or released on flights that haven't left. */
    public boolean isSellable() {
        return this == SCHEDULED || this == DELAYED;
    }
}
