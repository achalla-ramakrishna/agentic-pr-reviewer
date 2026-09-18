package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.FindingStatus;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.ReviewStatus;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.dto.ReviewResponse;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private FindingRepository findingRepository;
    @Mock private PracticeRepository practiceRepository;

    private ReviewQueryService service() {
        return new ReviewQueryService(reviewRepository, findingRepository, practiceRepository);
    }

    private Review review(UUID id) {
        return Review.builder()
                .id(id)
                .repoUrl("https://github.com/octocat/Hello-World")
                .status(ReviewStatus.COMPLETED)
                .totalFindings(2)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
    }

    @Test
    void assemblesSummaryAndJoinsPracticeDetailsForFindingsWithAPracticeId() {
        UUID reviewId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review(reviewId)));

        Finding ruleFinding =
                Finding.builder()
                        .id(UUID.randomUUID())
                        .reviewId(reviewId)
                        .practiceId(practiceId)
                        .source(FindingSource.RULE)
                        .category(Category.CORRECTNESS)
                        .severity(Severity.HIGH)
                        .filePath("Foo.java")
                        .lineStart(1)
                        .lineEnd(1)
                        .message("bad")
                        .status(FindingStatus.OPEN)
                        .createdAt(Instant.now())
                        .build();
        Finding llmFinding =
                Finding.builder()
                        .id(UUID.randomUUID())
                        .reviewId(reviewId)
                        .practiceId(null)
                        .source(FindingSource.LLM)
                        .category(Category.SECURITY)
                        .severity(Severity.LOW)
                        .filePath("Bar.java")
                        .message("something else")
                        .status(FindingStatus.OPEN)
                        .createdAt(Instant.now())
                        .build();
        when(findingRepository.findByReviewId(reviewId)).thenReturn(List.of(ruleFinding, llmFinding));

        Practice practice =
                Practice.builder()
                        .id(practiceId)
                        .practiceCode("JAVA-EXC-001")
                        .title("Empty catch block")
                        .description("d")
                        .category(Category.CORRECTNESS)
                        .severity(Severity.HIGH)
                        .technology(Technology.JAVA)
                        .build();
        when(practiceRepository.findAllById(any())).thenReturn(List.of(practice));

        ReviewResponse response = service().getReview(reviewId);

        assertThat(response.findings()).hasSize(2);
        var ruleResponse = response.findings().stream().filter(f -> f.filePath().equals("Foo.java")).findFirst().get();
        assertThat(ruleResponse.practiceCode()).isEqualTo("JAVA-EXC-001");
        assertThat(ruleResponse.practiceTitle()).isEqualTo("Empty catch block");

        var llmResponse = response.findings().stream().filter(f -> f.filePath().equals("Bar.java")).findFirst().get();
        assertThat(llmResponse.practiceCode()).isNull();

        assertThat(response.summary().bySeverity()).containsEntry(Severity.HIGH, 1L).containsEntry(Severity.LOW, 1L);
        assertThat(response.summary().byCategory())
                .containsEntry(Category.CORRECTNESS, 1L)
                .containsEntry(Category.SECURITY, 1L);
        assertThat(response.summary().ruleConfirmed()).isEqualTo(1L);
        assertThat(response.summary().llmOnly()).isEqualTo(1L);
    }

    @Test
    void throwsNotFoundForAMissingReview() {
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getReview(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReviewsReturnsOneResponsePerReview() {
        Review a = review(UUID.randomUUID());
        Review b = review(UUID.randomUUID());
        when(reviewRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b));
        when(findingRepository.findByReviewId(any())).thenReturn(List.of());

        List<ReviewResponse> responses = service().listReviews();

        assertThat(responses).hasSize(2);
    }
}
