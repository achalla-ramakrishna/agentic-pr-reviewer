package com.codewalnut.prreviewer.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.rule.RuleFinding;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindingMergerTest {

    private static final UUID PRACTICE_ID = UUID.randomUUID();

    private RuleFinding ruleFinding(String file, int lineStart, int lineEnd, Category category) {
        return new RuleFinding(PRACTICE_ID, "JAVA-EXC-001", category, Severity.HIGH, file, lineStart, lineEnd, "rule message");
    }

    private LlmFinding llmFinding(String file, Integer lineStart, Integer lineEnd, Category category, String practiceCode) {
        return new LlmFinding(practiceCode, category, Severity.MEDIUM, file, lineStart, lineEnd, "llm message");
    }

    @Test
    void ruleOnlyWhenNoLlmFindingMatches() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)), List.of(), Map.of());

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(FindingSource.RULE);
        assertThat(merged.get(0).practiceId()).isEqualTo(PRACTICE_ID);
    }

    @Test
    void llmOnlyWhenNoRuleFindingMatches() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(),
                        List.of(llmFinding("Foo.java", 5, 5, Category.SECURITY, null)),
                        Map.of());

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(FindingSource.LLM);
        assertThat(merged.get(0).practiceId()).isNull();
    }

    @Test
    void bothWhenSameFileCategoryAndOverlappingLines() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 12, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", 11, 13, Category.CORRECTNESS, "JAVA-EXC-001")),
                        Map.of());

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(FindingSource.BOTH);
        // Rule's fields (exact line numbers, resolved practiceId) win for a BOTH match.
        assertThat(merged.get(0).practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(merged.get(0).lineStart()).isEqualTo(10);
    }

    @Test
    void bothWhenLinesAreCloseButNotOverlapping() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", 12, 12, Category.CORRECTNESS, null)), // 2 lines away
                        Map.of());

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(FindingSource.BOTH);
    }

    @Test
    void notBothWhenLinesAreTooFarApart() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", 50, 50, Category.CORRECTNESS, null)),
                        Map.of());

        assertThat(merged).hasSize(2);
        assertThat(merged).extracting(MergedFinding::source)
                .containsExactlyInAnyOrder(FindingSource.RULE, FindingSource.LLM);
    }

    @Test
    void notBothWhenDifferentCategory() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", 10, 10, Category.SECURITY, null)),
                        Map.of());

        assertThat(merged).hasSize(2);
    }

    @Test
    void notBothWhenDifferentFile() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)),
                        List.of(llmFinding("Bar.java", 10, 10, Category.CORRECTNESS, null)),
                        Map.of());

        assertThat(merged).hasSize(2);
    }

    @Test
    void llmFindingWithNullLinesNeverMatchesARuleFinding() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", null, null, Category.CORRECTNESS, null)),
                        Map.of());

        assertThat(merged).hasSize(2);
    }

    @Test
    void eachLlmFindingCanOnlyMergeIntoOneRuleFinding() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(
                                ruleFinding("Foo.java", 10, 10, Category.CORRECTNESS),
                                ruleFinding("Foo.java", 11, 11, Category.CORRECTNESS)),
                        List.of(llmFinding("Foo.java", 10, 10, Category.CORRECTNESS, null)), // could match either
                        Map.of());

        assertThat(merged).hasSize(2);
        assertThat(merged).extracting(MergedFinding::source)
                .containsExactlyInAnyOrder(FindingSource.BOTH, FindingSource.RULE);
    }

    @Test
    void resolvesLlmOnlyFindingPracticeCodeToAnId() {
        UUID resolvedId = UUID.randomUUID();
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(),
                        List.of(llmFinding("Foo.java", 1, 1, Category.STYLE, "SPRING-DI-001")),
                        Map.of("SPRING-DI-001", resolvedId));

        assertThat(merged.get(0).practiceId()).isEqualTo(resolvedId);
    }

    @Test
    void llmOnlyFindingWithUnresolvablePracticeCodeGetsNullPracticeId() {
        List<MergedFinding> merged =
                FindingMerger.merge(
                        List.of(),
                        List.of(llmFinding("Foo.java", 1, 1, Category.STYLE, "NOT-IN-MAP")),
                        Map.of());

        assertThat(merged.get(0).practiceId()).isNull();
    }
}
