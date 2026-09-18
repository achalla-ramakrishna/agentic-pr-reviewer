package com.codewalnut.prreviewer.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeContextSelectorTest {

    private Practice practice(String code, String detectionPattern) {
        return Practice.builder()
                .practiceCode(code)
                .title(code + " title")
                .description("desc")
                .category(Category.CORRECTNESS)
                .severity(Severity.MEDIUM)
                .technology(Technology.JAVA)
                .detectionPattern(detectionPattern)
                .build();
    }

    @Test
    void keepsOnlyNonDeterministicPractices() {
        List<Practice> candidates =
                List.of(
                        practice("JAVA-A", "catch\\s*\\(\\)"), // real regex, rule engine already handles it
                        practice("JAVA-B", "NO_CLEAN_REGEX: needs semantic judgment"),
                        practice("JAVA-C", "NO_ANNOTATION_HEURISTIC: needs control flow"));

        List<Practice> selected = PracticeContextSelector.select(candidates);

        assertThat(selected).extracting(Practice::getPracticeCode).containsExactly("JAVA-B", "JAVA-C");
    }

    @Test
    void capsResultAtMaxPracticesPerFile() {
        List<Practice> candidates = new ArrayList<>();
        for (int i = 0; i < PracticeContextSelector.MAX_PRACTICES_PER_FILE + 10; i++) {
            candidates.add(practice(String.format("JAVA-%03d", i), "NO_CLEAN_REGEX: needs judgment"));
        }

        List<Practice> selected = PracticeContextSelector.select(candidates);

        assertThat(selected).hasSize(PracticeContextSelector.MAX_PRACTICES_PER_FILE);
    }
}
