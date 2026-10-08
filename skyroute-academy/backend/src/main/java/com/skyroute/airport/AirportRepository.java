package com.skyroute.airport;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AirportRepository extends MongoRepository<Airport, String> {

    /** Case-insensitive prefix match on city or code, e.g. "dal" -> Dallas. Derived via @Query JSON. */
    @Query("{ '$or': [ { 'city': { '$regex': ?0, '$options': 'i' } }, { '_id': { '$regex': ?0, '$options': 'i' } } ] }")
    List<Airport> searchByPrefix(String anchoredPattern);
}
