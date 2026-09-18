package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.dto.FindingFeedbackRequest;
import com.codewalnut.prreviewer.dto.FindingResponse;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Records a reviewer's accept/reject decision on a finding. This is the
 * write side the dashboard (chunk 7) calls; chunk 8 builds the aggregate
 * precision/recurring-category metrics on top of the decisions recorded
 * here -- it doesn't need its own write path for the same data.
 */
@Service
public class FindingService {

    private final FindingRepository findingRepository;
    private final PracticeRepository practiceRepository;

    public FindingService(FindingRepository findingRepository, PracticeRepository practiceRepository) {
        this.findingRepository = findingRepository;
        this.practiceRepository = practiceRepository;
    }

    public FindingResponse recordFeedback(UUID id, FindingFeedbackRequest request) {
        Finding finding =
                findingRepository.findById(id).orElseThrow(() -> new NotFoundException("Finding not found: " + id));

        finding.setStatus(request.status());
        finding.setFeedbackReason(request.feedbackReason());
        finding.setDecidedBy(request.decidedBy());
        finding.setDecidedAt(Instant.now());
        finding = findingRepository.save(finding);

        Practice practice =
                finding.getPracticeId() != null ? practiceRepository.findById(finding.getPracticeId()).orElse(null) : null;
        return FindingResponse.from(
                finding, practice != null ? practice.getPracticeCode() : null, practice != null ? practice.getTitle() : null);
    }
}
