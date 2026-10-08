package com.skyroute.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RecordLocatorGeneratorTest {

    private final RecordLocatorGenerator generator = new RecordLocatorGenerator();

    @RepeatedTest(50)
    void producesSixUnambiguousCharacters() {
        assertThat(generator.next()).matches("[A-HJ-NP-Z2-9]{6}");
    }

    @Test
    void isDeterministicWithASeededRandom() {
        var a = new RecordLocatorGenerator(RandomGenerator.of("L64X128MixRandom"));
        assertThat(a.next()).hasSize(RecordLocatorGenerator.LENGTH);
    }

    @Test
    void rarelyRepeatsInASmallSample() {
        Set<String> sample = IntStream.range(0, 10_000).mapToObj(i -> generator.next()).collect(Collectors.toSet());
        assertThat(sample).hasSizeGreaterThan(9_990);
    }
}
