package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.dto.ReviewRequest;
import com.codewalnut.prreviewer.dto.ReviewResponse;
import com.codewalnut.prreviewer.service.ReviewOrchestrator;
import com.codewalnut.prreviewer.service.ReviewQueryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The full, persisted review flow: submit a repo/PR URL, get back a Review
 * with its merged rule+LLM findings, and fetch it (or any past review)
 * again later. This is what the dashboard (chunk 7) will call -- the
 * per-stage preview endpoints (/api/github/diff, /api/rules/review,
 * /api/llm/review) remain as standalone diagnostics for inspecting one
 * pipeline stage in isolation.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewOrchestrator reviewOrchestrator;
    private final ReviewQueryService reviewQueryService;

    public ReviewController(ReviewOrchestrator reviewOrchestrator, ReviewQueryService reviewQueryService) {
        this.reviewOrchestrator = reviewOrchestrator;
        this.reviewQueryService = reviewQueryService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> submit(@Valid @RequestBody ReviewRequest request) {
        Review review = reviewOrchestrator.submitReview(request.url(), request.token(), request.requestedBy());
        ReviewResponse response = reviewQueryService.getReview(review.getId());
        return ResponseEntity.created(URI.create("/api/reviews/" + review.getId())).body(response);
    }

    @GetMapping("/{id}")
    public ReviewResponse get(@PathVariable UUID id) {
        return reviewQueryService.getReview(id);
    }

    @GetMapping
    public List<ReviewResponse> list() {
        return reviewQueryService.listReviews();
    }
}
