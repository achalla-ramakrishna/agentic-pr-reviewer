package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.client.OpenAiApiException;
import com.codewalnut.prreviewer.client.OpenAiClient;
import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.ReviewStatus;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {

    @Mock private GitHubService gitHubService;
    @Mock private RuleEngineService ruleEngineService;
    @Mock private LlmReviewService llmReviewService;
    @Mock private PracticeRepository practiceRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private FindingRepository findingRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private ReviewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        orchestrator =
                new ReviewOrchestrator(
                        gitHubService,
                        ruleEngineService,
                        llmReviewService,
                        practiceRepository,
                        reviewRepository,
                        findingRepository,
                        transactionManager);
    }

    private GitHubDiff sampleDiff() {
        ChangedFile file = new ChangedFile("Foo.java", null, ChangedFileStatus.MODIFIED, null, "class Foo {}", 1, 0);
        return new GitHubDiff("octocat", "Hello-World", "abc123", 7, List.of(file), false);
    }

    @Test
    void persistsReviewAndFindingsOnFullSuccess() {
        when(gitHubService.resolve("url", "token")).thenReturn(sampleDiff());
        when(ruleEngineService.review(any())).thenReturn(List.of());
        when(llmReviewService.review(any())).thenReturn(List.of());
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(
                        invocation -> {
                            Review r = invocation.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        Review result = orchestrator.submitReview("url", "token", "alice");

        assertThat(result.getRepoUrl()).isEqualTo("url");
        assertThat(result.getPrNumber()).isEqualTo(7);
        assertThat(result.getCommitSha()).isEqualTo("abc123");
        assertThat(result.getRequestedBy()).isEqualTo("alice");
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();

        verify(reviewRepository).save(any(Review.class));
        verify(findingRepository).saveAll(anyList());
    }

    @Test
    void savesFindingsWithTheReviewsGeneratedId() {
        when(gitHubService.resolve(any(), any())).thenReturn(sampleDiff());
        UUID practiceId = UUID.randomUUID();
        com.codewalnut.prreviewer.rule.RuleFinding ruleFinding =
                new com.codewalnut.prreviewer.rule.RuleFinding(
                        practiceId, "JAVA-EXC-001", Category.CORRECTNESS, Severity.HIGH, "Foo.java", 1, 1, "bad");
        when(ruleEngineService.review(any())).thenReturn(List.of(ruleFinding));
        when(llmReviewService.review(any())).thenReturn(List.of());

        UUID generatedReviewId = UUID.randomUUID();
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(
                        invocation -> {
                            Review r = invocation.getArgument(0);
                            r.setId(generatedReviewId);
                            return r;
                        });

        orchestrator.submitReview("url", null, null);

        ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository).saveAll(captor.capture());
        List<Finding> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getReviewId()).isEqualTo(generatedReviewId);
        assertThat(saved.get(0).getPracticeId()).isEqualTo(practiceId);
        assertThat(saved.get(0).getSource()).isEqualTo(FindingSource.RULE);
    }

    @Test
    void resolvesLlmFindingsPracticeCodeBeforePersisting() {
        when(gitHubService.resolve(any(), any())).thenReturn(sampleDiff());
        when(ruleEngineService.review(any())).thenReturn(List.of());
        com.codewalnut.prreviewer.llm.LlmFinding llmFinding =
                new com.codewalnut.prreviewer.llm.LlmFinding(
                        "SPRING-DI-001", Category.STYLE, Severity.LOW, "Foo.java", 5, 5, "field injection");
        when(llmReviewService.review(any())).thenReturn(List.of(llmFinding));

        UUID resolvedPracticeId = UUID.randomUUID();
        Practice practice =
                Practice.builder()
                        .id(resolvedPracticeId)
                        .practiceCode("SPRING-DI-001")
                        .title("t")
                        .description("d")
                        .category(Category.STYLE)
                        .severity(Severity.LOW)
                        .technology(Technology.SPRING)
                        .build();
        when(practiceRepository.findByPracticeCodeIn(any())).thenReturn(List.of(practice));
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(
                        invocation -> {
                            Review r = invocation.getArgument(0);
                            r.setId(UUID.randomUUID());
                            return r;
                        });

        orchestrator.submitReview("url", null, null);

        ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getPracticeId()).isEqualTo(resolvedPracticeId);
    }

    @Test
    void propagatesGitHubFailureAndPersistsNothing() {
        when(gitHubService.resolve(any(), any()))
                .thenThrow(new com.codewalnut.prreviewer.client.GitHubNotFoundException("not found"));

        assertThatThrownBy(() -> orchestrator.submitReview("url", null, null))
                .isInstanceOf(com.codewalnut.prreviewer.client.GitHubNotFoundException.class);

        verify(reviewRepository, never()).save(any());
        verify(findingRepository, never()).saveAll(any());
    }

    @Test
    void propagatesLlmFailureAndPersistsNothing() {
        when(gitHubService.resolve(any(), any())).thenReturn(sampleDiff());
        when(ruleEngineService.review(any())).thenReturn(List.of());
        when(llmReviewService.review(any())).thenThrow(new OpenAiApiException("boom"));

        assertThatThrownBy(() -> orchestrator.submitReview("url", null, null))
                .isInstanceOf(OpenAiApiException.class);

        verify(reviewRepository, never()).save(any());
        verify(findingRepository, never()).saveAll(any());
    }
}
