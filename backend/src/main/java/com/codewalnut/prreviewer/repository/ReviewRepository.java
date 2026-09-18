package com.codewalnut.prreviewer.repository;

import com.codewalnut.prreviewer.domain.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Paging/filtering by repo or status can be added when the dashboard (chunk 7/8) needs it. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findAllByOrderByCreatedAtDesc();
}
