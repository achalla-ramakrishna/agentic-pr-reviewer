package com.codewalnut.prreviewer.repository;

import com.codewalnut.prreviewer.domain.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Full query API (by repo, by status, paged) lands with chunk 6's orchestrator. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {}
