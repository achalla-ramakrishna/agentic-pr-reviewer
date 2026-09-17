package com.codewalnut.prreviewer.client;

import java.util.List;

/**
 * The result of resolving a repo/PR URL: which repo, which commit, which
 * files changed (or, in whole-repo mode, which files exist), and whether
 * the file list was capped before we hit every file (see
 * GitHubService.MAX_FILES) rather than silently reviewing only part of a
 * large repo without saying so.
 */
public record GitHubDiff(
        String owner,
        String repo,
        String ref,
        Integer prNumber,
        List<ChangedFile> files,
        boolean truncated) {}
