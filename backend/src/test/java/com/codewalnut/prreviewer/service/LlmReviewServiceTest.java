package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.client.OpenAiApiException;
import com.codewalnut.prreviewer.client.OpenAiClient;
import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmReviewServiceTest {

    @Mock private PracticeRepository practiceRepository;
    @Mock private OpenAiClient openAiClient;

    private LlmReviewService service;

    @BeforeEach
    void setUp() {
        for (Technology technology : Technology.values()) {
            lenient().when(practiceRepository.findByTechnologyAndActiveTrue(technology)).thenReturn(List.of());
        }
        service = new LlmReviewService(practiceRepository, openAiClient);
    }

    private Practice sentinelPractice(String code) {
        return Practice.builder()
                .practiceCode(code)
                .title(code + " title")
                .description("desc")
                .category(Category.CORRECTNESS)
                .severity(Severity.HIGH)
                .technology(Technology.JAVA)
                .detectionPattern("NO_CLEAN_REGEX: needs semantic judgment")
                .build();
    }

    private GitHubDiff diffOf(ChangedFile... files) {
        return new GitHubDiff("octocat", "Hello-World", "main", null, List.of(files), false);
    }

    @Test
    void skipsRemovedFilesWithoutCallingOpenAi() {
        ChangedFile removed =
                new ChangedFile("Deleted.java", null, ChangedFileStatus.REMOVED, "@@ -1 +0,0 @@\n-x", null, 0, 1);

        assertThat(service.review(diffOf(removed))).isEmpty();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void skipsFilesWithNoContentAndNoPatch() {
        ChangedFile empty = new ChangedFile("Binary.png", null, ChangedFileStatus.ADDED, null, null, 0, 0);

        assertThat(service.review(diffOf(empty))).isEmpty();
        verifyNoInteractions(openAiClient);
    }

    @Test
    void callsOpenAiOncePerEligibleFileAndAggregatesFindings() {
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(sentinelPractice("JAVA-EXC-099")));
        when(openAiClient.chatCompletion(anyString(), anyString()))
                .thenReturn("{\"findings\":[{\"category\":\"CORRECTNESS\",\"severity\":\"HIGH\",\"message\":\"issue\"}]}");

        ChangedFile a = new ChangedFile("A.java", null, ChangedFileStatus.MODIFIED, null, "class A {}", 1, 0);
        ChangedFile b = new ChangedFile("B.java", null, ChangedFileStatus.MODIFIED, null, "class B {}", 1, 0);

        List<LlmFinding> findings = service.review(diffOf(a, b));

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(LlmFinding::filePath).containsExactlyInAnyOrder("A.java", "B.java");
        verify(openAiClient, times(2)).chatCompletion(anyString(), anyString());
    }

    @Test
    void promptIncludesOnlyRetrievedSentinelPracticesForThatFilesTechnology() {
        when(practiceRepository.findByTechnologyAndActiveTrue(Technology.JAVA))
                .thenReturn(List.of(sentinelPractice("JAVA-EXC-099")));
        when(openAiClient.chatCompletion(anyString(), anyString())).thenReturn("{\"findings\":[]}");

        ChangedFile file = new ChangedFile("A.java", null, ChangedFileStatus.MODIFIED, null, "class A {}", 1, 0);
        service.review(diffOf(file));

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).chatCompletion(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).contains("JAVA-EXC-099");
    }

    @Test
    void propagatesOpenAiFailureRatherThanSwallowingIt() {
        when(practiceRepository.findByTechnologyAndActiveTrue(any())).thenReturn(List.of());
        when(openAiClient.chatCompletion(anyString(), anyString()))
                .thenThrow(new OpenAiApiException("OpenAI unreachable"));

        ChangedFile file = new ChangedFile("A.java", null, ChangedFileStatus.MODIFIED, null, "class A {}", 1, 0);

        assertThatThrownBy(() -> service.review(diffOf(file))).isInstanceOf(OpenAiApiException.class);
    }
}
