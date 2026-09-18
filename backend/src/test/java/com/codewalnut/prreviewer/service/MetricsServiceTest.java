package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.FindingStatus;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.dto.CategoryCount;
import com.codewalnut.prreviewer.dto.MetricsResponse;
import com.codewalnut.prreviewer.dto.PrecisionStats;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private FindingRepository findingRepository;

    private MetricsService service() {
        return new MetricsService(reviewRepository, findingRepository);
    }

    private Review review(Instant createdAt) {
        return Review.builder().id(UUID.randomUUID()).repoUrl("https://github.com/o/r").createdAt(createdAt).build();
    }

    private Finding finding(Category category, FindingSource source, FindingStatus status) {
        return Finding.builder()
                .id(UUID.randomUUID())
                .reviewId(UUID.randomUUID())
                .source(source)
                .category(category)
                .severity(Severity.HIGH)
                .filePath("Foo.java")
                .message("bad")
                .status(status)
                .build();
    }

    @Test
    void countsReviewsPerDay() {
        Instant day1 = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant day2 = LocalDate.of(2026, 1, 2).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(reviewRepository.findAll())
                .thenReturn(List.of(review(day1), review(day1), review(day2.plusSeconds(3600))));
        when(findingRepository.findAll()).thenReturn(List.of());

        MetricsResponse metrics = service().getMetrics();

        assertThat(metrics.totalReviews()).isEqualTo(3);
        assertThat(metrics.reviewsOverTime())
                .extracting("date", "count")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 1), 2L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 2), 1L));
    }

    @Test
    void computesOverallAndPerSourcePrecisionFromAcceptRejectFeedback() {
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(findingRepository.findAll())
                .thenReturn(
                        List.of(
                                finding(Category.SECURITY, FindingSource.RULE, FindingStatus.ACCEPTED),
                                finding(Category.SECURITY, FindingSource.RULE, FindingStatus.REJECTED),
                                finding(Category.SECURITY, FindingSource.LLM, FindingStatus.ACCEPTED),
                                finding(Category.SECURITY, FindingSource.LLM, FindingStatus.ACCEPTED),
                                finding(Category.SECURITY, FindingSource.LLM, FindingStatus.REJECTED),
                                finding(Category.SECURITY, FindingSource.LLM, FindingStatus.REJECTED),
                                finding(Category.SECURITY, FindingSource.LLM, FindingStatus.REJECTED),
                                finding(Category.CORRECTNESS, FindingSource.RULE, FindingStatus.OPEN)));

        MetricsResponse metrics = service().getMetrics();

        assertThat(metrics.totalFindings()).isEqualTo(8);
        assertThat(metrics.overallPrecision()).isEqualTo(PrecisionStats.of(3, 4, 1));
        assertThat(metrics.precisionBySource().get(FindingSource.RULE)).isEqualTo(PrecisionStats.of(1, 1, 1));
        assertThat(metrics.precisionBySource().get(FindingSource.LLM)).isEqualTo(PrecisionStats.of(2, 3, 0));
        assertThat(metrics.precisionBySource().get(FindingSource.BOTH)).isEqualTo(PrecisionStats.of(0, 0, 0));
    }

    @Test
    void ranksTopCategoriesByFindingCountDescending() {
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(findingRepository.findAll())
                .thenReturn(
                        List.of(
                                finding(Category.STYLE, FindingSource.RULE, FindingStatus.OPEN),
                                finding(Category.SECURITY, FindingSource.RULE, FindingStatus.OPEN),
                                finding(Category.SECURITY, FindingSource.RULE, FindingStatus.OPEN),
                                finding(Category.SECURITY, FindingSource.RULE, FindingStatus.OPEN)));

        MetricsResponse metrics = service().getMetrics();

        assertThat(metrics.topCategories())
                .containsExactly(new CategoryCount(Category.SECURITY, 3), new CategoryCount(Category.STYLE, 1));
    }

    @Test
    void reportsNullPrecisionWhenNothingHasBeenDecidedYet() {
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(findingRepository.findAll())
                .thenReturn(List.of(finding(Category.CORRECTNESS, FindingSource.RULE, FindingStatus.OPEN)));

        MetricsResponse metrics = service().getMetrics();

        assertThat(metrics.overallPrecision().precision()).isNull();
    }
}
