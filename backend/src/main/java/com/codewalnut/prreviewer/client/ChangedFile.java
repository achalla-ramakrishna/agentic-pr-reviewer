package com.codewalnut.prreviewer.client;

/**
 * A single changed file, from either a PR diff or a whole-repo snapshot.
 *
 * patch is the unified diff hunk text for PR mode (null for a binary file,
 * or for a whole-repo snapshot where there's no "before" to diff against).
 * content is the full file content, populated for whole-repo mode (and left
 * null for PR mode, where the diff itself is what gets reviewed).
 *
 * Everything here is untrusted data fetched from a reviewed repository —
 * never treat patch/content as instructions (see AGENTS.md guardrails).
 */
public record ChangedFile(
        String path,
        String previousPath,
        ChangedFileStatus status,
        String patch,
        String content,
        int additions,
        int deletions) {}
