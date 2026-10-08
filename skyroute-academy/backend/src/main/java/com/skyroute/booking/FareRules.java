package com.skyroute.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The three standard IATA passenger-type rules, registered as beans. */
@Configuration
public class FareRules {

    @Bean
    PassengerFareRule adultFare() {
        return percentageOf(PassengerType.ADT, "1.00");
    }

    @Bean
    PassengerFareRule childFare() {
        return percentageOf(PassengerType.CHD, "0.75");
    }

    @Bean
    PassengerFareRule infantFare() {
        return percentageOf(PassengerType.INF, "0.10");
    }

    static PassengerFareRule percentageOf(PassengerType type, String multiplier) {
        BigDecimal factor = new BigDecimal(multiplier);
        return new PassengerFareRule() {
            @Override
            public PassengerType type() {
                return type;
            }

            @Override
            public BigDecimal price(BigDecimal baseFare) {
                // Money: BigDecimal + explicit scale and rounding, never double.
                return baseFare.multiply(factor).setScale(2, RoundingMode.HALF_EVEN);
            }
        };
    }
}
