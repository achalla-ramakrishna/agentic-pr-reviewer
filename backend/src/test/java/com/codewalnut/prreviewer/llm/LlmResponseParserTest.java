package com.codewalnut.prreviewer.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmResponseParserTest {

    @Test
    void parsesEmptyFindingsArray() {
        List<LlmFinding> findings = LlmResponseParser.parse("{\"findings\":[]}", "Foo.java");

        assertThat(findings).isEmpty();
    }

    @Test
    void parsesAFindingAndAlwaysUsesTheSuppliedFilePathNotTheModels() {
        String json =
                """
                {"findings":[
                  {"lineStart":10,"lineEnd":12,"category":"CORRECTNESS","severity":"HIGH",
                   "message":"Empty catch block","practiceCode":"JAVA-EXC-001"}
                ]}""";

        List<LlmFinding> findings = LlmResponseParser.parse(json, "Foo.java");

        assertThat(findings).hasSize(1);
        LlmFinding finding = findings.get(0);
        assertThat(finding.filePath()).isEqualTo("Foo.java");
        assertThat(finding.lineStart()).isEqualTo(10);
        assertThat(finding.lineEnd()).isEqualTo(12);
        assertThat(finding.category()).isEqualTo(Category.CORRECTNESS);
        assertThat(finding.severity()).isEqualTo(Severity.HIGH);
        assertThat(finding.message()).isEqualTo("Empty catch block");
        assertThat(finding.practiceCode()).isEqualTo("JAVA-EXC-001");
    }

    @Test
    void parsesAFindingWithNullPracticeCodeAndNullLines() {
        String json =
                """
                {"findings":[
                  {"lineStart":null,"lineEnd":null,"category":"STYLE","severity":"LOW",
                   "message":"Not from the reference list","practiceCode":null}
                ]}""";

        List<LlmFinding> findings = LlmResponseParser.parse(json, "Foo.java");

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).practiceCode()).isNull();
        assertThat(findings.get(0).lineStart()).isNull();
    }

    @Test
    void throwsOnBlankResponse() {
        assertThatThrownBy(() -> LlmResponseParser.parse("", "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
        assertThatThrownBy(() -> LlmResponseParser.parse(null, "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
    }

    @Test
    void throwsOnNonJsonGarbage() {
        assertThatThrownBy(() -> LlmResponseParser.parse("not json at all", "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
    }

    @Test
    void throwsWhenFindingsKeyMissing() {
        assertThatThrownBy(() -> LlmResponseParser.parse("{\"other\":[]}", "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
    }

    @Test
    void throwsOnInvalidEnumValue() {
        String json = "{\"findings\":[{\"category\":\"NOT_A_CATEGORY\",\"severity\":\"HIGH\",\"message\":\"x\"}]}";

        assertThatThrownBy(() -> LlmResponseParser.parse(json, "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
    }

    @Test
    void throwsWhenRequiredFieldMissing() {
        String json = "{\"findings\":[{\"category\":\"CORRECTNESS\",\"severity\":\"HIGH\"}]}"; // no message

        assertThatThrownBy(() -> LlmResponseParser.parse(json, "Foo.java"))
                .isInstanceOf(LlmResponseParseException.class);
    }
}
