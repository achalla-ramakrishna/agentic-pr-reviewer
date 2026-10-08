package com.skyroute.booking;

/** IATA passenger type codes: adult (12+), child (2-11), infant on lap (&lt;2, no seat). */
public enum PassengerType {
    ADT(true), CHD(true), INF(false);

    private final boolean occupiesSeat;

    PassengerType(boolean occupiesSeat) {
        this.occupiesSeat = occupiesSeat;
    }

    public boolean occupiesSeat() {
        return occupiesSeat;
    }
}
