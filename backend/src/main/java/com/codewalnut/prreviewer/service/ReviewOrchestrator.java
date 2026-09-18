package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.ReviewStatus;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.repository.ReviewRepository;
import com.codewalnut.prreviewer.review.FindingMerger;
import com.codewalnut.prreviewer.review.MergedFinding;
import com.codewalnut.prreviewer.rule.RuleFinding;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ties together the pipeline SPEC.md describes: resolve the diff, run the
 * rule engine (chunk 4) and the LLM pass (chunk 5) independently against it,
 * merge their findings (marking BOTH where they agree, see FindingMerger),
 * and persist a Review + its Findings.
 *
 * Only the final persistence step runs inside a database transaction, via
 * TransactionTemplate rather than a class-level @Transactional -- wrapping
 * the whole method would hold a connection open across the GitHub/OpenAI
 * network calls for no benefit, and calling a @Transactional method through
 * "this" would silently skip Spring's proxy anyway (see SPRING-CORR-004 in
 * the practices KB). If any upstream step throws, nothing is persisted and
 * the exception propagates as-is, so a caller never mistakes a partial
 * result for a complete review (per SPEC's boundary conditions).
 */
@Service
public class ReviewOrchestrator {

    private final GitHubService gitHubService;
    private final RuleEngineService ruleEngineService;
    private final LlmReviewService llmReviewService;
    private final PracticeRepository practiceRepository;
    private final ReviewRepository reviewRepository;
    private final FindingRepository findingRepository;
    private final TransactionTemplate transactionTemplate;

    public ReviewOrchestrator(
            GitHubService gitHubService,
            RuleEngineService ruleEngineService,
            LlmReviewService llmReviewService,
            PracticeRepository practiceRepository,
            ReviewRepository reviewRepository,
            FindingRepository findingRepository,
            PlatformTransactionManager transactionManager) {
        this.gitHubService = gitHubService;
        this.ruleEngineService = ruleEngineService;
        this.llmReviewService = llmReviewService;
        this.practiceRepository = practiceRepository;
        this.reviewRepository = reviewRepository;
        this.findingRepository = findingRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Review submitReview(String url, String token, String requestedBy) {
        GitHubDiff diff = gitHubService.resolve(url, token);
        List<RuleFinding> ruleFindings = ruleEngineService.review(diff);
        List<LlmFinding> llmFindings = llmReviewService.review(diff);

        Map<String, UUID> practiceCodeToId = resolvePracticeCodes(llmFindings);
        List<MergedFinding> merged = FindingMerger.merge(ruleFindings, llmFindings, practiceCodeToId);

        return transactionTemplate.execute(
                status -> persist(url, diff.prNumber(), diff.ref(), requestedBy, merged));
    }

    private Review persist(
            String repoUrl, Integer prNumber, String commitSha, String requestedBy, List<MergedFinding> merged) {
        Review review =
                Review.builder()
                        .repoUrl(repoUrl)
                        .prNumber(prNumber)
                        .commitSha(commitSha)
                        .requestedBy(requestedBy)
                        .status(ReviewStatus.COMPLETED)
                        .totalFindings(merged.size())
                        .completedAt(Instant.now())
                        .build();
        review = reviewRepository.save(review);

        UUID reviewId = review.getId();
        List<Finding> findings =
                merged.stream()
                        .map(
                                m ->
                                        Finding.builder()
                                                .reviewId(reviewId)
                                                .practiceId(m.practiceId())
                                                .source(m.source())
                                                .category(m.category())
                                                .severity(m.severity())
                                                .filePath(m.filePath())
                                                .lineStart(m.lineStart())
                                                .lineEnd(m.lineEnd())
                                                .message(m.message())
                                                .build())
                        .collect(Collectors.toList());
        findingRepository.saveAll(findings);

        return review;
    }

    private Map<String, UUID> resolvePracticeCodes(List<LlmFinding> llmFindings) {
        Set<String> codes =
                llmFindings.stream()
                        .map(LlmFinding::practiceCode)
                        .filter(code -> code != null && !code.isBlank())
                        .collect(Collectors.toSet());
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> result = new HashMap<>();
        for (Practice practice : practiceRepository.findByPracticeCodeIn(codes)) {
            result.put(practice.getPracticeCode(), practice.getId());
        }
        return result;
    }
}
