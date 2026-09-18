package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.rule.RuleFinding;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    private static final String EMPTY_CATCH_PATTERN = "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}";
    private static final String SECRET_PATTERN = "(password|apiKey|secret|token)\\s*=\\s*\"[^\"]{8,}\"";

    @Mock private PracticeRepository practiceRepository;

    private RuleEngineService service;

    @BeforeEach
    void setUp() {
        // Default every technology to "no practices" so a test only needs to stub the
        // ones it cares about; lenient() because most tests only exercise a few of these.
        for (Technology technology : Technology.values()) {
            lenient().when(practiceRepository.findByTechnologyAndActiveTrue(technology)).thenReturn(List.of());
        }
        clearInvocations(practiceRepository); // stubbing itself counts as an invocation otherwise
        service = new RuleEngineService(practiceRepository);
    }

    private Practice emptyCatchPractice() {
        return Practice.builder()
                .id(UUID.randomUUID())
                .practiceCode("JAVA-EXC-001")
                .title("Empty catch block swallows exceptions")
                .description("desc")
                .category(Category.CORRECTNESS)
                .severity(Severity.HIGH)
                .technology(Technology.JAVA)
                .detectionPattern(EMPTY_CATCH_PATTERN)
                .build();
    }

    private GitHubDiff diffOf(ChangedFile file) {
        return new GitHubDiff("octocat", "Hello-World", "main", null, List.of(file), false);
    }

    @Test
    void flagsABadPatternOnAnAddedLineInPrMode() {
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(emptyCatchPractice()));

        String patch =
                "@@ -1,3 +1,5 @@\n"
                        + " public void process(Order order) {\n"
                        + "-    doSomething();\n"
                        + "+    try {\n"
                        + "+        doSomething();\n"
                        + "+    } catch (Exception e) {}\n"
                        + " }\n";
        ChangedFile file =
                new ChangedFile("Order.java", null, ChangedFileStatus.MODIFIED, patch, null, 3, 1);

        List<RuleFinding> findings = service.review(diffOf(file));

        assertThat(findings).hasSize(1);
        RuleFinding finding = findings.get(0);
        assertThat(finding.practiceCode()).isEqualTo("JAVA-EXC-001");
        assertThat(finding.filePath()).isEqualTo("Order.java");
        assertThat(finding.lineStart()).isEqualTo(4);
        assertThat(finding.lineEnd()).isEqualTo(4);
    }

    @Test
    void doesNotFlagBadPatternConfinedToContextLines() {
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(emptyCatchPractice()));

        // Every line here is unchanged context (no '+'/'-') -- the bad pattern
        // predates this PR, so it shouldn't be reported as something this diff introduced.
        String patch = "@@ -1,3 +1,3 @@\n" + " try {\n" + " doSomething();\n" + " } catch (Exception e) {}\n";
        ChangedFile file =
                new ChangedFile("Order.java", null, ChangedFileStatus.MODIFIED, patch, null, 0, 0);

        assertThat(service.review(diffOf(file))).isEmpty();
    }

    @Test
    void flagsBadPatternAnywhereInWholeRepoMode() {
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(emptyCatchPractice()));

        String content =
                "public class Foo {\n"
                        + "    void bar() {\n"
                        + "        try {\n"
                        + "            doSomething();\n"
                        + "        } catch (Exception e) {}\n"
                        + "    }\n"
                        + "}\n";
        ChangedFile file = new ChangedFile("Foo.java", null, ChangedFileStatus.ADDED, null, content, 7, 0);

        List<RuleFinding> findings = service.review(diffOf(file));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).lineStart()).isEqualTo(5);
        assertThat(findings.get(0).lineEnd()).isEqualTo(5);
    }

    @Test
    void generalPracticeAppliesRegardlessOfFileExtension() {
        Practice hardcodedSecret =
                Practice.builder()
                        .id(UUID.randomUUID())
                        .practiceCode("GEN-SEC-001")
                        .title("Hardcoded credential or API key in source")
                        .description("desc")
                        .category(Category.SECURITY)
                        .severity(Severity.CRITICAL)
                        .technology(Technology.GENERAL)
                        .detectionPattern(SECRET_PATTERN)
                        .build();
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.GENERAL))
                .thenReturn(List.of(hardcodedSecret));

        ChangedFile file =
                new ChangedFile(
                        "config.yml",
                        null,
                        ChangedFileStatus.ADDED,
                        null,
                        "apiKey = \"abcdefgh1234\"\n",
                        1,
                        0);

        List<RuleFinding> findings = service.review(diffOf(file));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).practiceCode()).isEqualTo("GEN-SEC-001");
    }

    @Test
    void skipsNonDeterministicSentinelPatternsWithoutError() {
        Practice sentinelPractice =
                Practice.builder()
                        .id(UUID.randomUUID())
                        .practiceCode("HIB-CONC-001")
                        .title("Missing @Version field allows lost updates")
                        .description("desc")
                        .category(Category.CORRECTNESS)
                        .severity(Severity.HIGH)
                        .technology(Technology.JAVA)
                        .detectionPattern("NO_CLEAN_REGEX: @Entity class with mutable fields and no @Version")
                        .build();
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(sentinelPractice));

        ChangedFile file =
                new ChangedFile("Account.java", null, ChangedFileStatus.ADDED, null, "@Entity\nclass Account {}\n", 2, 0);

        assertThat(service.review(diffOf(file))).isEmpty();
    }

    @Test
    void skipsRemovedFilesEntirelyWithoutQueryingPractices() {
        ChangedFile removed =
                new ChangedFile("Deleted.java", null, ChangedFileStatus.REMOVED, "@@ -1,1 +0,0 @@\n-old();\n", null, 0, 1);

        assertThat(service.review(diffOf(removed))).isEmpty();
        verifyNoInteractions(practiceRepository);
    }
}
