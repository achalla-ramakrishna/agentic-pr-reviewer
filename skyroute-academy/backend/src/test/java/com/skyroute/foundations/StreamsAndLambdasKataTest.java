package com.skyroute.foundations;

import static java.util.stream.Collectors.averagingInt;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Course Module 1 kata: core Java 17 + Streams + lambdas on airline data.
 * Each test is one exercise. To practise: delete the body after "// solution",
 * re-implement it, and make the test green again.
 */
class StreamsAndLambdasKataTest {

    /** Java 16+ record: immutable data carrier with equals/hashCode/toString. */
    record Leg(String flight, String origin, String destination, int minutes, int seatsSold, int capacity) {
        double loadFactor() {
            return (double) seatsSold / capacity;
        }
    }

    /** Java 17 sealed hierarchy: the compiler knows every subtype, so switches can be exhaustive. */
    sealed interface Fare permits Published, Negotiated, Award {
    }

    record Published(int amount) implements Fare {
    }

    record Negotiated(int amount, int discountPercent) implements Fare {
    }

    record Award(int miles) implements Fare {
    }

    private static final List<Leg> LEGS = List.of(
            new Leg("SR101", "DFW", "JFK", 215, 140, 150),
            new Leg("SR103", "DFW", "JFK", 220, 90, 150),
            new Leg("SR202", "JFK", "LHR", 415, 270, 280),
            new Leg("SR301", "DFW", "LAX", 200, 60, 150),
            new Leg("SR404", "LAX", "NRT", 700, 300, 350));

    @Test
    void ex1_filterMapCollect_flightsOutOfDfwSortedByDuration() {
        // solution
        List<String> result = LEGS.stream()
                .filter(l -> l.origin().equals("DFW"))
                .sorted(Comparator.comparingInt(Leg::minutes))
                .map(Leg::flight)
                .toList();
        assertThat(result).containsExactly("SR301", "SR101", "SR103");
    }

    @Test
    void ex2_groupingBy_seatsSoldPerOrigin() {
        // solution
        Map<String, Integer> result = LEGS.stream()
                .collect(groupingBy(Leg::origin, TreeMap::new, summingInt(Leg::seatsSold)));
        assertThat(result).containsExactly(Map.entry("DFW", 290), Map.entry("JFK", 270), Map.entry("LAX", 300));
    }

    @Test
    void ex3_partitioningBy_fullFlights() {
        // solution: "full" means load factor >= 90%
        Map<Boolean, Long> result = LEGS.stream().collect(partitioningBy(l -> l.loadFactor() >= 0.9, counting()));
        assertThat(result).containsEntry(true, 2L).containsEntry(false, 3L);
    }

    @Test
    void ex4_toMap_withMergeFunction_busiestFlightPerRoute() {
        // solution
        Map<String, String> result = LEGS.stream().collect(toMap(
                l -> l.origin() + "-" + l.destination(),
                Function.identity(),
                (a, b) -> a.seatsSold() >= b.seatsSold() ? a : b,
                TreeMap::new)).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().flight()));
        assertThat(result).containsEntry("DFW-JFK", "SR101").hasSize(4);
    }

    @Test
    void ex5_reduceAndOptional_longestFlight() {
        // solution
        Optional<Leg> longest = LEGS.stream().max(Comparator.comparingInt(Leg::minutes));
        assertThat(longest.map(Leg::flight).orElse("none")).isEqualTo("SR404");
        int totalMinutes = LEGS.stream().map(Leg::minutes).reduce(0, Integer::sum);
        assertThat(totalMinutes).isEqualTo(1750);
    }

    @Test
    void ex6_functionalInterfaces_composeValidators() {
        // solution: Predicate composition, Function chaining, Supplier, UnaryOperator, BiFunction
        Predicate<String> threeLetters = s -> s.length() == 3;
        Predicate<String> upper = s -> s.equals(s.toUpperCase());
        Predicate<String> iata = threeLetters.and(upper);
        UnaryOperator<String> trim = String::trim;
        Function<String, String> normalise = trim.andThen(String::toUpperCase);
        BiFunction<String, String, String> route = (o, d) -> o + "-" + d;
        Supplier<String> fallback = () -> "UNKNOWN";

        assertThat(iata.test(normalise.apply(" dfw "))).isTrue();
        assertThat(iata.test("dfw")).isFalse();
        assertThat(route.apply("DFW", "JFK")).isEqualTo("DFW-JFK");
        assertThat(Optional.<String>empty().orElseGet(fallback)).isEqualTo("UNKNOWN");
    }

    @Test
    void ex7_sealedTypesAndPatternMatching_priceInCash() {
        // solution: Java 17 has pattern matching for instanceof; switch patterns arrive in Java 21.
        Function<Fare, Integer> cashValue = fare -> {
            if (fare instanceof Published p) {
                return p.amount();
            } else if (fare instanceof Negotiated n) {
                return n.amount() * (100 - n.discountPercent()) / 100;
            } else if (fare instanceof Award a) {
                return a.miles() / 100; // 1 cent per mile
            }
            throw new IllegalStateException("unreachable for a sealed type");
        };
        int total = List.of(new Published(200), new Negotiated(200, 10), new Award(25_000)).stream()
                .mapToInt(cashValue::apply).sum();
        assertThat(total).isEqualTo(200 + 180 + 250);
    }

    @Test
    void ex8_textBlocksAndJoining_manifest() {
        // solution
        String manifest = LEGS.stream().limit(2).map(Leg::flight).collect(joining(", ", "[", "]"));
        String expected = """
                [SR101, SR103]""";
        assertThat(manifest).isEqualTo(expected);
        double avg = LEGS.stream().collect(averagingInt(Leg::minutes));
        assertThat(avg).isEqualTo(350.0);
    }
}
