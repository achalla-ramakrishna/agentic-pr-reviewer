package com.skyroute.booking;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FarePricingService {

    private final Map<PassengerType, PassengerFareRule> rules;

    public FarePricingService(List<PassengerFareRule> rules) {
        this.rules = rules.stream().collect(Collectors.toMap(
                PassengerFareRule::type, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Two fare rules for " + a.type());
                },
                () -> new EnumMap<>(PassengerType.class)));
        if (this.rules.size() != PassengerType.values().length) {
            throw new IllegalStateException("Missing fare rule; have " + this.rules.keySet());
        }
    }

    /** Total for all passengers on one segment: sum of per-passenger prices. */
    public BigDecimal priceSegment(BigDecimal baseFare, List<Passenger> passengers) {
        return passengers.stream()
                .map(p -> rules.get(p.type()).price(baseFare))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
