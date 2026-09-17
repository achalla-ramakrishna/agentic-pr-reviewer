package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubClient;
import com.codewalnut.prreviewer.client.GitHubClient.GitTreeEntry;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.client.GitHubTarget;
import com.codewalnut.prreviewer.client.GitHubUrlParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves a GitHub repo/PR URL into a {@link GitHubDiff} the rest of the
 * pipeline can review. Two modes:
 *
 * <ul>
 *   <li>PR mode: the URL names a pull request — fetch its changed-file
 *       diffs directly.
 *   <li>Whole-repo mode: the URL names a repo (optionally at a branch/tag)
 *       — there's no "before" to diff against, so every text file under the
 *       size cap is fetched in full and reviewed as-is.
 * </ul>
 *
 * Both modes cap how much they fetch (see MAX_FILES/MAX_FILE_BYTES) rather
 * than silently reviewing only part of a large repo without saying so —
 * {@link GitHubDiff#truncated()} tells the caller when that happened.
 */
@Service
public class GitHubService {

    static final int MAX_FILES = 200;
    static final long MAX_FILE_BYTES = 200_000;

    private final GitHubClient client;

    public GitHubService(GitHubClient client) {
        this.client = client;
    }

    public GitHubDiff resolve(String url, String token) {
        GitHubTarget target = GitHubUrlParser.parse(url);
        return switch (target) {
            case GitHubTarget.PullRequest pr -> resolvePullRequest(pr, token);
            case GitHubTarget.Repository repo -> resolveRepository(repo, token);
        };
    }

    private GitHubDiff resolvePullRequest(GitHubTarget.PullRequest pr, String token) {
        GitHubClient.PullRequestMetadata metadata =
                client.fetchPullRequest(pr.owner(), pr.repo(), pr.number(), token);
        List<ChangedFile> files =
                client.fetchPullRequestFiles(pr.owner(), pr.repo(), pr.number(), token);
        boolean truncated = files.size() >= MAX_FILES;
        List<ChangedFile> capped = files.size() > MAX_FILES ? files.subList(0, MAX_FILES) : files;
        return new GitHubDiff(pr.owner(), pr.repo(), metadata.head().sha(), pr.number(), capped, truncated);
    }

    private GitHubDiff resolveRepository(GitHubTarget.Repository repo, String token) {
        String ref = repo.ref();
        if (ref == null) {
            GitHubClient.RepositoryMetadata metadata =
                    client.fetchRepository(repo.owner(), repo.repo(), token);
            ref = metadata.default_branch();
        }

        GitHubClient.GitTree tree = client.fetchTree(repo.owner(), repo.repo(), ref, token);

        List<GitTreeEntry> blobs =
                tree.tree().stream().filter(entry -> "blob".equals(entry.type())).toList();
        boolean truncated = tree.truncated() || blobs.size() > MAX_FILES;
        List<GitTreeEntry> capped = blobs.size() > MAX_FILES ? blobs.subList(0, MAX_FILES) : blobs;

        List<ChangedFile> files = new ArrayList<>();
        for (GitTreeEntry entry : capped) {
            if (entry.size() != null && entry.size() > MAX_FILE_BYTES) {
                files.add(new ChangedFile(entry.path(), null, ChangedFileStatus.ADDED, null, null, 0, 0));
                continue;
            }
            String content = client.fetchFileContent(repo.owner(), repo.repo(), entry.path(), ref, token);
            files.add(new ChangedFile(entry.path(), null, ChangedFileStatus.ADDED, null, content, 0, 0));
        }

        return new GitHubDiff(repo.owner(), repo.repo(), ref, null, files, truncated);
    }
}
