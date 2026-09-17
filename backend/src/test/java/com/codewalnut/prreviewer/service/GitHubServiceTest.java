package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubClient;
import com.codewalnut.prreviewer.client.GitHubClient.GitTree;
import com.codewalnut.prreviewer.client.GitHubClient.GitTreeEntry;
import com.codewalnut.prreviewer.client.GitHubClient.PrRef;
import com.codewalnut.prreviewer.client.GitHubClient.PullRequestMetadata;
import com.codewalnut.prreviewer.client.GitHubClient.RepositoryMetadata;
import com.codewalnut.prreviewer.client.GitHubDiff;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock private GitHubClient client;

    private GitHubService service() {
        return new GitHubService(client);
    }

    @Test
    void resolvesPullRequestUrlByFetchingMetadataAndFiles() {
        when(client.fetchPullRequest("octocat", "Hello-World", 1, "tok"))
                .thenReturn(new PullRequestMetadata(1, "Test PR", new PrRef("abc123", "feature"), new PrRef("def", "main")));
        when(client.fetchPullRequestFiles("octocat", "Hello-World", 1, "tok"))
                .thenReturn(
                        List.of(
                                new ChangedFile("src/Main.java", null, ChangedFileStatus.MODIFIED, "@@ -1 +1 @@", null, 1, 1)));

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World/pull/1", "tok");

        assertThat(diff.owner()).isEqualTo("octocat");
        assertThat(diff.repo()).isEqualTo("Hello-World");
        assertThat(diff.ref()).isEqualTo("abc123");
        assertThat(diff.prNumber()).isEqualTo(1);
        assertThat(diff.files()).hasSize(1);
        assertThat(diff.truncated()).isFalse();
        verify(client, never()).fetchRepository(anyString(), anyString(), any());
    }

    @Test
    void resolvesBareRepoUrlUsingDefaultBranch() {
        when(client.fetchRepository("octocat", "Hello-World", "tok"))
                .thenReturn(new RepositoryMetadata("Hello-World", "octocat/Hello-World", "main"));
        when(client.fetchTree("octocat", "Hello-World", "main", "tok"))
                .thenReturn(
                        new GitTree(
                                "main",
                                List.of(
                                        new GitTreeEntry("README.md", "blob", 11L),
                                        new GitTreeEntry("src", "tree", null)),
                                false));
        when(client.fetchFileContent("octocat", "Hello-World", "README.md", "main", "tok"))
                .thenReturn("hello world");

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World", "tok");

        assertThat(diff.ref()).isEqualTo("main");
        assertThat(diff.prNumber()).isNull();
        assertThat(diff.files()).hasSize(1); // the "tree" entry is skipped, only the blob is fetched
        assertThat(diff.files().get(0).content()).isEqualTo("hello world");
        assertThat(diff.truncated()).isFalse();
    }

    @Test
    void resolvesRepoUrlWithExplicitRefWithoutFetchingDefaultBranch() {
        when(client.fetchTree(eq("octocat"), eq("Hello-World"), eq("develop"), any()))
                .thenReturn(new GitTree("develop", List.of(), false));

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World/tree/develop", null);

        assertThat(diff.ref()).isEqualTo("develop");
        verify(client, never()).fetchRepository(anyString(), anyString(), any());
    }

    @Test
    void skipsFetchingContentForFilesOverTheSizeCapButStillListsThem() {
        when(client.fetchTree(anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        new GitTree(
                                "main",
                                List.of(new GitTreeEntry("huge.bin", "blob", GitHubService.MAX_FILE_BYTES + 1)),
                                false));

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World/tree/main", null);

        assertThat(diff.files()).hasSize(1);
        assertThat(diff.files().get(0).content()).isNull();
        verify(client, never()).fetchFileContent(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void capsPullRequestFilesAtMaxAndMarksTruncated() {
        List<ChangedFile> manyFiles = new ArrayList<>();
        for (int i = 0; i < GitHubService.MAX_FILES + 20; i++) {
            manyFiles.add(new ChangedFile("file" + i + ".java", null, ChangedFileStatus.ADDED, "patch", null, 1, 0));
        }
        when(client.fetchPullRequest(anyString(), anyString(), eq(1), any()))
                .thenReturn(new PullRequestMetadata(1, "Big PR", new PrRef("sha", "feature"), new PrRef("sha2", "main")));
        when(client.fetchPullRequestFiles(anyString(), anyString(), eq(1), any())).thenReturn(manyFiles);

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World/pull/1", null);

        assertThat(diff.files()).hasSize(GitHubService.MAX_FILES);
        assertThat(diff.truncated()).isTrue();
    }

    @Test
    void marksTruncatedWhenGitHubItselfTruncatedTheTree() {
        when(client.fetchTree(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GitTree("main", List.of(), true));

        GitHubDiff diff = service().resolve("https://github.com/octocat/Hello-World/tree/main", null);

        assertThat(diff.truncated()).isTrue();
    }
}
