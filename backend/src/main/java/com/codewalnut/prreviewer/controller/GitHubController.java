package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.dto.GitHubDiffRequest;
import com.codewalnut.prreviewer.service.GitHubService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preview endpoint for chunk 3: resolves a repo/PR URL into the diff/files
 * that would be reviewed. Doesn't persist anything yet — a Review record is
 * created once the rule engine + LLM orchestrator (chunks 4-6) exist to
 * actually produce findings from this.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @PostMapping("/diff")
    public GitHubDiff diff(@Valid @RequestBody GitHubDiffRequest request) {
        return gitHubService.resolve(request.url(), request.token());
    }
}
