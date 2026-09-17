package com.codewalnut.prreviewer.repository;

import com.codewalnut.prreviewer.domain.Finding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByReviewId(UUID reviewId);
}
