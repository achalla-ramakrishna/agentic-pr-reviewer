package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.dto.GitHubDiffRequest;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.service.GitHubService;
import com.codewalnut.prreviewer.service.LlmReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preview endpoint for chunk 5: resolves a repo/PR URL and runs the LLM
 * review pass against it, without persisting anything -- a Review record is
 * created once the orchestrator (chunk 6) exists to merge rule and LLM
 * findings together. The OpenAI API key is server-side config
 * (OPENAI_API_KEY), unlike the GitHub token which is per-request.
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final GitHubService gitHubService;
    private final LlmReviewService llmReviewService;

    public LlmController(GitHubService gitHubService, LlmReviewService llmReviewService) {
        this.gitHubService = gitHubService;
        this.llmReviewService = llmReviewService;
    }

    @PostMapping("/review")
    public List<LlmFinding> review(@Valid @RequestBody GitHubDiffRequest request) {
        GitHubDiff diff = gitHubService.resolve(request.url(), request.token());
        return llmReviewService.review(diff);
    }
}
