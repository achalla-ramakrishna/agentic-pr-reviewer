package com.codewalnut.prreviewer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.client.OpenAiClient;
import com.codewalnut.prreviewer.dto.ReviewRequest;
import com.codewalnut.prreviewer.dto.ReviewResponse;
import com.codewalnut.prreviewer.service.GitHubService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full-stack test through the real rule engine and the real (H2-seeded)
 * practices table -- only the network boundary (GitHub, OpenAI) is mocked,
 * same approach as PracticeControllerTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private GitHubService gitHubService;

    @MockBean
    private OpenAiClient openAiClient;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void submitPersistsAndReturnsAReviewWithMergedFindings() {
        String emptyCatchBlock = "try {\n    doSomething();\n} catch (Exception e) {\n}\n"; // matches JAVA-EXC-001
        ChangedFile file = new ChangedFile("Foo.java", null, ChangedFileStatus.ADDED, null, emptyCatchBlock, 4, 0);
        when(gitHubService.resolve(anyString(), any()))
                .thenReturn(new GitHubDiff("octocat", "Hello-World", "main", null, List.of(file), false));
        when(openAiClient.chatCompletion(anyString(), anyString())).thenReturn("{\"findings\":[]}");

        ReviewRequest request = new ReviewRequest("https://github.com/octocat/Hello-World", null, "tester");
        ResponseEntity<ReviewResponse> created =
                rest.postForEntity(url("/api/reviews"), request, ReviewResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getLocation().toString()).contains("/api/reviews/");

        ReviewResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.requestedBy()).isEqualTo("tester");
        assertThat(body.totalFindings()).isGreaterThanOrEqualTo(1);
        assertThat(body.findings()).anyMatch(f -> f.filePath().equals("Foo.java"));
        assertThat(body.findings())
                .anyMatch(f -> "JAVA-EXC-001".equals(f.practiceCode()));

        ResponseEntity<ReviewResponse> fetched =
                rest.getForEntity(url("/api/reviews/" + body.id()), ReviewResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().id()).isEqualTo(body.id());
    }

    @Test
    void getReturns404ForUnknownId() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/reviews/" + UUID.randomUUID()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listIncludesSubmittedReviews() {
        when(gitHubService.resolve(anyString(), any()))
                .thenReturn(new GitHubDiff("octocat", "Hello-World", "main", null, List.of(), false));
        when(openAiClient.chatCompletion(anyString(), anyString())).thenReturn("{\"findings\":[]}");
        rest.postForEntity(
                url("/api/reviews"),
                new ReviewRequest("https://github.com/octocat/Hello-World", null, null),
                ReviewResponse.class);

        ResponseEntity<ReviewResponse[]> list = rest.getForEntity(url("/api/reviews"), ReviewResponse[].class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotNull();
        assertThat(list.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsBlankUrl() {
        ResponseEntity<Map> response =
                rest.postForEntity(url("/api/reviews"), new ReviewRequest("", null, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
