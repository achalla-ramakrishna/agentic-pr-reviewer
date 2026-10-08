package com.skyroute.booking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookingRepository extends MongoRepository<Booking, String> {

    Optional<Booking> findByRecordLocator(String recordLocator);

    /** Served by the agent_recent index {createdBy:1, createdAt:-1}. */
    List<Booking> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);
}
