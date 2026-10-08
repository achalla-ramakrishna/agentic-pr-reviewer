package com.skyroute.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FarePricingServiceTest {

    private final FareRules rules = new FareRules();
    private final FarePricingService pricing = new FarePricingService(
            List.of(rules.adultFare(), rules.childFare(), rules.infantFare()));

    @Test
    void pricesEachPassengerTypeAndSums() {
        var family = List.of(
                new Passenger("Ana", "Diaz", PassengerType.ADT, null),
                new Passenger("Leo", "Diaz", PassengerType.CHD, null),
                new Passenger("Mia", "Diaz", PassengerType.INF, null));

        // 200 + 150 (75%) + 20 (10%)
        assertThat(pricing.priceSegment(new BigDecimal("200.00"), family)).isEqualByComparingTo("370.00");
    }

    @Test
    void roundsHalfEvenToCents() {
        var child = List.of(new Passenger("Leo", "Diaz", PassengerType.CHD, null));
        // 99.99 * 0.75 = 74.9925 -> 74.99
        assertThat(pricing.priceSegment(new BigDecimal("99.99"), child)).isEqualByComparingTo("74.99");
    }

    @Test
    void failsFastWhenARuleIsMissing() {
        var adultOnly = List.of(rules.adultFare());
        assertThatThrownBy(() -> new FarePricingService(adultOnly))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing fare rule");
    }
}
