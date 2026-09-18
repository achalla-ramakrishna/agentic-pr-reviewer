package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.dto.FindingFeedbackRequest;
import com.codewalnut.prreviewer.dto.FindingResponse;
import com.codewalnut.prreviewer.service.FindingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/findings")
public class FindingController {

    private final FindingService findingService;

    public FindingController(FindingService findingService) {
        this.findingService = findingService;
    }

    @PatchMapping("/{id}/feedback")
    public FindingResponse recordFeedback(@PathVariable UUID id, @Valid @RequestBody FindingFeedbackRequest request) {
        return findingService.recordFeedback(id, request);
    }
}
