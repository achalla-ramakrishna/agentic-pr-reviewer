package com.skyroute.booking

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Spock (Groovy) specification: given/when/then blocks and data tables make
 * business rules readable by non-developers. Run with `mvn test` like any JUnit test.
 */
class FarePricingSpec extends Specification {

    def rules = new FareRules()
    def pricing = new FarePricingService([rules.adultFare(), rules.childFare(), rules.infantFare()])

    @Unroll
    def "a #type passenger pays #expected on a #base base fare"() {
        given:
        def passenger = new Passenger("Test", "Pax", type, null)

        when:
        def price = pricing.priceSegment(new BigDecimal(base), [passenger])

        then:
        price == new BigDecimal(expected)

        where:
        type              | base     || expected
        PassengerType.ADT | "200.00" || "200.00"
        PassengerType.CHD | "200.00" || "150.00"
        PassengerType.INF | "200.00" || "20.00"
        PassengerType.CHD | "99.99"  || "74.99"
    }

    def "infants do not occupy seats but adults and children do"() {
        expect:
        PassengerType.ADT.occupiesSeat()
        PassengerType.CHD.occupiesSeat()
        !PassengerType.INF.occupiesSeat()
    }

    def "booking cannot be cancelled twice"() {
        given:
        def booking = new Booking("ABC234", "a@b.com", [new Passenger("A", "B", PassengerType.ADT, null)],
                [], BigDecimal.TEN, "USD", "agent")
        booking.cancel(java.time.Instant.now())

        when:
        booking.cancel(java.time.Instant.now())

        then:
        def ex = thrown(com.skyroute.common.error.BusinessRuleException)
        ex.errorCode == "ALREADY_CANCELLED"
    }
}
