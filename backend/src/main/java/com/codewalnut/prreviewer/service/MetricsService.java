package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.FindingStatus;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.dto.CategoryCount;
import com.codewalnut.prreviewer.dto.MetricsResponse;
import com.codewalnut.prreviewer.dto.PrecisionStats;
import com.codewalnut.prreviewer.dto.ReviewCountByDay;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Aggregates across every persisted Review/Finding for the dashboard's
 * metrics tab. Loads whole tables and aggregates in Java rather than via SQL
 * grouping -- fine at this app's declared scale (single shared PAT,
 * single-team dashboard, per SPEC's "not in scope" section); revisit with a
 * DB-side aggregate query if that scale assumption changes.
 */
@Service
public class MetricsService {

    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;

    public MetricsService(ReviewRepository reviewRepository, FindingRepository findingRepository) {
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
    }

    public MetricsResponse getMetrics() {
        List<Review> reviews = reviewRepository.findAll();
        List<Finding> findings = findingRepository.findAll();

        return new MetricsResponse(
                reviews.size(),
                findings.size(),
                reviewsOverTime(reviews),
                precisionOf(findings),
                precisionBySource(findings),
                topCategories(findings));
    }

    private List<ReviewCountByDay> reviewsOverTime(List<Review> reviews) {
        Map<LocalDate, Long> byDay =
                reviews.stream()
                        .collect(
                                Collectors.groupingBy(
                                        review -> review.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                                        TreeMap::new,
                                        Collectors.counting()));
        return byDay.entrySet().stream().map(e -> new ReviewCountByDay(e.getKey(), e.getValue())).toList();
    }

    private Map<FindingSource, PrecisionStats> precisionBySource(List<Finding> findings) {
        Map<FindingSource, PrecisionStats> bySource = new EnumMap<>(FindingSource.class);
        for (FindingSource source : FindingSource.values()) {
            List<Finding> forSource = findings.stream().filter(f -> f.getSource() == source).toList();
            bySource.put(source, precisionOf(forSource));
        }
        return bySource;
    }

    private PrecisionStats precisionOf(List<Finding> findings) {
        long accepted = findings.stream().filter(f -> f.getStatus() == FindingStatus.ACCEPTED).count();
        long rejected = findings.stream().filter(f -> f.getStatus() == FindingStatus.REJECTED).count();
        long open = findings.stream().filter(f -> f.getStatus() == FindingStatus.OPEN).count();
        return PrecisionStats.of(accepted, rejected, open);
    }

    private List<CategoryCount> topCategories(List<Finding> findings) {
        Map<Category, Long> byCategory =
                findings.stream().collect(Collectors.groupingBy(Finding::getCategory, Collectors.counting()));
        return byCategory.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
                .toList();
    }
}
