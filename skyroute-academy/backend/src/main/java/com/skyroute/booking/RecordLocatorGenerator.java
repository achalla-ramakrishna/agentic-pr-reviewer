package com.skyroute.booking;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Generates 6-character record locators like "K7QXPM". The alphabet drops 0/O and 1/I
 * because PNRs are read out over the phone. 32^6 ~ 1 billion combinations; collisions
 * are still possible, so the unique index + retry in BookingService is the real guarantee.
 */
@Component
public class RecordLocatorGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    private final RandomGenerator random;

    public RecordLocatorGenerator() {
        this(new SecureRandom());
    }

    RecordLocatorGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        return random.ints(LENGTH, 0, ALPHABET.length())
                .mapToObj(ALPHABET::charAt)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }
}
