package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.dto.FindingResponse;
import com.codewalnut.prreviewer.dto.ReviewResponse;
import com.codewalnut.prreviewer.dto.ReviewSummary;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Read side of the review pipeline -- assembling the DTO the dashboard (chunk 7) will render. */
@Service
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final PracticeRepository practiceRepository;

    public ReviewQueryService(
            ReviewRepository reviewRepository,
            FindingRepository findingRepository,
            PracticeRepository practiceRepository) {
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.practiceRepository = practiceRepository;
    }

    public ReviewResponse getReview(UUID id) {
        Review review =
                reviewRepository.findById(id).orElseThrow(() -> new NotFoundException("Review not found: " + id));
        List<Finding> findings = findingRepository.findByReviewId(id);
        return assemble(review, findings);
    }

    public List<ReviewResponse> listReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(review -> assemble(review, findingRepository.findByReviewId(review.getId())))
                .collect(Collectors.toList());
    }

    private ReviewResponse assemble(Review review, List<Finding> findings) {
        List<UUID> practiceIds =
                findings.stream().map(Finding::getPracticeId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Practice> practicesById =
                practiceRepository.findAllById(practiceIds).stream()
                        .collect(Collectors.toMap(Practice::getId, Function.identity()));

        List<FindingResponse> findingResponses =
                findings.stream()
                        .map(
                                finding -> {
                                    Practice practice =
                                            finding.getPracticeId() != null
                                                    ? practicesById.get(finding.getPracticeId())
                                                    : null;
                                    return FindingResponse.from(
                                            finding,
                                            practice != null ? practice.getPracticeCode() : null,
                                            practice != null ? practice.getTitle() : null);
                                })
                        .collect(Collectors.toList());

        Map<Severity, Long> bySeverity =
                findings.stream().collect(Collectors.groupingBy(Finding::getSeverity, Collectors.counting()));
        Map<Category, Long> byCategory =
                findings.stream().collect(Collectors.groupingBy(Finding::getCategory, Collectors.counting()));
        long ruleConfirmed = findings.stream().filter(f -> f.getSource() != FindingSource.LLM).count();
        long llmOnly = findings.stream().filter(f -> f.getSource() == FindingSource.LLM).count();

        return ReviewResponse.from(
                review, findingResponses, new ReviewSummary(bySeverity, byCategory, ruleConfirmed, llmOnly));
    }
}
