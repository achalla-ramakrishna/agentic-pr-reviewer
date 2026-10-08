package com.skyroute.booking;

import java.math.BigDecimal;

/**
 * Strategy for pricing one passenger type. Adding a new type (e.g. YTH youth) means
 * adding a new @Component — FarePricingService doesn't change (Open/Closed), and it
 * depends on this abstraction, not on concrete rules (Dependency Inversion).
 */
public interface PassengerFareRule {

    PassengerType type();

    BigDecimal price(BigDecimal baseFare);
}
