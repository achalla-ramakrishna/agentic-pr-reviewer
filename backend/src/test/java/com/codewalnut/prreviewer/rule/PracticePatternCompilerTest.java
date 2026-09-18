package com.codewalnut.prreviewer.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PracticePatternCompilerTest {

    private Practice practiceWithPattern(String detectionPattern) {
        return Practice.builder()
                .practiceCode("TEST-001")
                .title("Test practice")
                .description("desc")
                .category(Category.CORRECTNESS)
                .severity(Severity.MEDIUM)
                .technology(Technology.JAVA)
                .detectionPattern(detectionPattern)
                .build();
    }

    @Test
    void compilesAValidRegex() {
        Optional<Pattern> pattern = PracticePatternCompiler.compile(practiceWithPattern("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"));

        assertThat(pattern).isPresent();
        assertThat(pattern.get().matcher("catch (Exception e) {}").find()).isTrue();
    }

    @Test
    void skipsNoCleanRegexSentinel() {
        assertThat(PracticePatternCompiler.compile(practiceWithPattern("NO_CLEAN_REGEX: needs type info")))
                .isEmpty();
    }

    @Test
    void skipsNoAnnotationHeuristicSentinel() {
        assertThat(
                        PracticePatternCompiler.compile(
                                practiceWithPattern("NO_ANNOTATION_HEURISTIC: needs control flow")))
                .isEmpty();
    }

    @Test
    void skipsNoAssertionHeuristicSentinel() {
        assertThat(
                        PracticePatternCompiler.compile(
                                practiceWithPattern("NO_ASSERTION_HEURISTIC: needs method body analysis")))
                .isEmpty();
    }

    @Test
    void skipsInvalidRegexWithoutThrowing() {
        assertThat(PracticePatternCompiler.compile(practiceWithPattern("[unterminated"))).isEmpty();
    }

    @Test
    void skipsNullOrBlankPattern() {
        assertThat(PracticePatternCompiler.compile(practiceWithPattern(null))).isEmpty();
        assertThat(PracticePatternCompiler.compile(practiceWithPattern("   "))).isEmpty();
    }
}
