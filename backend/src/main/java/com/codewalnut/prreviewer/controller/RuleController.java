package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.dto.GitHubDiffRequest;
import com.codewalnut.prreviewer.rule.RuleFinding;
import com.codewalnut.prreviewer.service.GitHubService;
import com.codewalnut.prreviewer.service.RuleEngineService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preview endpoint for chunk 4: resolves a repo/PR URL and runs the rule
 * engine against it, without persisting anything -- a Review record is
 * created once the LLM pass + orchestrator (chunks 5-6) exist to merge rule
 * and LLM findings together.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final GitHubService gitHubService;
    private final RuleEngineService ruleEngineService;

    public RuleController(GitHubService gitHubService, RuleEngineService ruleEngineService) {
        this.gitHubService = gitHubService;
        this.ruleEngineService = ruleEngineService;
    }

    @PostMapping("/review")
    public List<RuleFinding> review(@Valid @RequestBody GitHubDiffRequest request) {
        GitHubDiff diff = gitHubService.resolve(request.url(), request.token());
        return ruleEngineService.review(diff);
    }
}
