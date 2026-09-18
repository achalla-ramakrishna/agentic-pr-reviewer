package com.codewalnut.prreviewer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.FindingStatus;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.dto.FindingFeedbackRequest;
import com.codewalnut.prreviewer.dto.FindingResponse;
import com.codewalnut.prreviewer.repository.FindingRepository;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindingServiceTest {

    @Mock private FindingRepository findingRepository;
    @Mock private PracticeRepository practiceRepository;

    private FindingService service() {
        return new FindingService(findingRepository, practiceRepository);
    }

    private Finding finding(UUID id, UUID practiceId) {
        return Finding.builder()
                .id(id)
                .reviewId(UUID.randomUUID())
                .practiceId(practiceId)
                .source(FindingSource.RULE)
                .category(Category.CORRECTNESS)
                .severity(Severity.HIGH)
                .filePath("Foo.java")
                .lineStart(1)
                .lineEnd(1)
                .message("bad")
                .status(FindingStatus.OPEN)
                .build();
    }

    @Test
    void recordsAcceptedFeedbackAndJoinsPracticeDetails() {
        UUID findingId = UUID.randomUUID();
        UUID practiceId = UUID.randomUUID();
        when(findingRepository.findById(findingId)).thenReturn(Optional.of(finding(findingId, practiceId)));
        when(findingRepository.save(any(Finding.class))).thenAnswer(inv -> inv.getArgument(0));
        Practice practice =
                Practice.builder()
                        .id(practiceId)
                        .practiceCode("JAVA-EXC-001")
                        .title("Empty catch block")
                        .description("d")
                        .category(Category.CORRECTNESS)
                        .severity(Severity.HIGH)
                        .technology(Technology.JAVA)
                        .build();
        when(practiceRepository.findById(practiceId)).thenReturn(Optional.of(practice));

        FindingResponse response =
                service().recordFeedback(findingId, new FindingFeedbackRequest(FindingStatus.ACCEPTED, "looks right", "alice"));

        assertThat(response.status()).isEqualTo(FindingStatus.ACCEPTED);
        assertThat(response.feedbackReason()).isEqualTo("looks right");
        assertThat(response.decidedBy()).isEqualTo("alice");
        assertThat(response.decidedAt()).isNotNull();
        assertThat(response.practiceCode()).isEqualTo("JAVA-EXC-001");
    }

    @Test
    void recordsRejectedFeedbackForAFindingWithNoPractice() {
        UUID findingId = UUID.randomUUID();
        when(findingRepository.findById(findingId)).thenReturn(Optional.of(finding(findingId, null)));
        when(findingRepository.save(any(Finding.class))).thenAnswer(inv -> inv.getArgument(0));

        FindingResponse response =
                service().recordFeedback(findingId, new FindingFeedbackRequest(FindingStatus.REJECTED, "false positive", "bob"));

        assertThat(response.status()).isEqualTo(FindingStatus.REJECTED);
        assertThat(response.practiceCode()).isNull();
    }

    @Test
    void throwsNotFoundForAMissingFinding() {
        UUID id = UUID.randomUUID();
        when(findingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().recordFeedback(id, new FindingFeedbackRequest(FindingStatus.ACCEPTED, null, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
