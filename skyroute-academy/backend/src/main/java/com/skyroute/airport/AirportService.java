package com.skyroute.airport;

import com.skyroute.common.config.CacheConfig;
import com.skyroute.common.error.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class AirportService {

    private final AirportRepository airports;

    public AirportService(AirportRepository airports) {
        this.airports = airports;
    }

    @Cacheable(CacheConfig.AIRPORTS)
    public Airport get(String code) {
        String normalized = code.toUpperCase(Locale.ROOT);
        return airports.findById(normalized).orElseThrow(() -> new NotFoundException("Airport", normalized));
    }

    public List<Airport> search(String query) {
        // Pattern.quote stops user input being interpreted as regex (ReDoS / injection).
        String anchored = "^" + Pattern.quote(query.trim());
        return airports.searchByPrefix(anchored).stream()
                .sorted(Comparator.comparing(Airport::code))
                .limit(20)
                .toList();
    }
}
