package com.skyroute.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Pins down the exact MongoDB filter we send — no database needed. If someone
 * "simplifies" the $elemMatch away, this test explains why that's a bug.
 */
class FlightSearchQueryBuilderTest {

    private final FlightSearchQueryBuilder builder = new FlightSearchQueryBuilder();

    @Test
    void buildsRouteDayAndCabinFilter() {
        var criteria = new FlightSearchCriteria("dfw", "jfk", LocalDate.parse("2026-11-01"),
                CabinClass.BUSINESS, 2, new BigDecimal("900"), null, null, null);

        var query = builder.build(criteria);
        Document filter = query.getQueryObject();

        assertThat(filter.getString("origin")).isEqualTo("DFW");
        assertThat(filter.getString("destination")).isEqualTo("JFK");
        assertThat(filter.get("departureTime", Document.class).keySet()).containsExactly("$gte", "$lt");
        List<?> cabinConditions = filter.get("cabins", Document.class)
                .get("$elemMatch", Document.class).getList("$and", Document.class);
        assertThat(cabinConditions).hasSize(3);
        assertThat(cabinConditions.get(0)).isEqualTo(new Document("cabinClass", "BUSINESS"));
        assertThat(cabinConditions.get(1)).isEqualTo(new Document("seatsAvailable", new Document("$gte", 2)));
        assertThat(cabinConditions.get(2).toString()).contains("$lte", "900");
        assertThat(query.getSortObject().toJson()).isEqualTo("{\"departureTime\": 1}");
        assertThat(query.getLimit()).isEqualTo(20);
    }

    @Test
    void omitsFareConditionWhenNoMaxFare() {
        var criteria = new FlightSearchCriteria("DFW", "JFK", LocalDate.parse("2026-11-01"),
                null, null, null, null, 2, 10);

        var query = builder.build(criteria);

        List<Document> cabinConditions = query.getQueryObject().get("cabins", Document.class)
                .get("$elemMatch", Document.class).getList("$and", Document.class);
        assertThat(cabinConditions).hasSize(2).doesNotContain(new Document("baseFare", null));
        assertThat(cabinConditions.get(0)).isEqualTo(new Document("cabinClass", "ECONOMY"));
        assertThat(query.getSkip()).isEqualTo(20);
    }
}
