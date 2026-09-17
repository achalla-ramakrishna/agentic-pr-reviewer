package com.codewalnut.prreviewer.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubUrlParserTest {

    @Test
    void parsesPullRequestUrl() {
        GitHubTarget target = GitHubUrlParser.parse("https://github.com/spring-projects/spring-boot/pull/42");

        assertThat(target).isInstanceOf(GitHubTarget.PullRequest.class);
        GitHubTarget.PullRequest pr = (GitHubTarget.PullRequest) target;
        assertThat(pr.owner()).isEqualTo("spring-projects");
        assertThat(pr.repo()).isEqualTo("spring-boot");
        assertThat(pr.number()).isEqualTo(42);
    }

    @Test
    void parsesPullRequestUrlWithTrailingSegmentAndSlash() {
        GitHubTarget target =
                GitHubUrlParser.parse("https://github.com/octocat/Hello-World/pull/7/files/");

        GitHubTarget.PullRequest pr = (GitHubTarget.PullRequest) target;
        assertThat(pr.owner()).isEqualTo("octocat");
        assertThat(pr.repo()).isEqualTo("Hello-World");
        assertThat(pr.number()).isEqualTo(7);
    }

    @Test
    void parsesBareRepoUrlAsRepositoryWithNullRef() {
        GitHubTarget target = GitHubUrlParser.parse("https://github.com/octocat/Hello-World");

        assertThat(target).isInstanceOf(GitHubTarget.Repository.class);
        GitHubTarget.Repository repo = (GitHubTarget.Repository) target;
        assertThat(repo.owner()).isEqualTo("octocat");
        assertThat(repo.repo()).isEqualTo("Hello-World");
        assertThat(repo.ref()).isNull();
    }

    @Test
    void stripsDotGitSuffix() {
        GitHubTarget.Repository repo =
                (GitHubTarget.Repository) GitHubUrlParser.parse("https://github.com/octocat/Hello-World.git");

        assertThat(repo.repo()).isEqualTo("Hello-World");
    }

    @Test
    void parsesTreeUrlAsRepositoryWithRef() {
        GitHubTarget.Repository repo =
                (GitHubTarget.Repository)
                        GitHubUrlParser.parse("https://github.com/octocat/Hello-World/tree/main");

        assertThat(repo.ref()).isEqualTo("main");
    }

    @Test
    void parsesTreeUrlWithSlashInBranchName() {
        GitHubTarget.Repository repo =
                (GitHubTarget.Repository)
                        GitHubUrlParser.parse(
                                "https://github.com/octocat/Hello-World/tree/feature/my-branch");

        assertThat(repo.ref()).isEqualTo("feature/my-branch");
    }

    @Test
    void acceptsWwwSubdomain() {
        GitHubTarget target = GitHubUrlParser.parse("https://www.github.com/octocat/Hello-World");
        assertThat(target).isInstanceOf(GitHubTarget.Repository.class);
    }

    @Test
    void rejectsNonGitHubHost() {
        assertThatThrownBy(() -> GitHubUrlParser.parse("https://gitlab.com/octocat/Hello-World"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("github.com");
    }

    @Test
    void rejectsUrlMissingRepo() {
        assertThatThrownBy(() -> GitHubUrlParser.parse("https://github.com/octocat"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> GitHubUrlParser.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> GitHubUrlParser.parse("not a url at all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidPrNumber() {
        assertThatThrownBy(() -> GitHubUrlParser.parse("https://github.com/octocat/Hello-World/pull/abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
