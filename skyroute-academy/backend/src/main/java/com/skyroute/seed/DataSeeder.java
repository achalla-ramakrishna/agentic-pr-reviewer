package com.skyroute.seed;

import com.skyroute.airport.Airport;
import com.skyroute.airport.AirportRepository;
import com.skyroute.auth.AppUser;
import com.skyroute.auth.AppUserRepository;
import com.skyroute.auth.Role;
import com.skyroute.flight.CabinClass;
import com.skyroute.flight.CabinInventory;
import com.skyroute.flight.Flight;
import com.skyroute.flight.FlightRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Loads demo data on startup when {@code skyroute.seed.enabled=true} (dev only!).
 * Idempotent: does nothing if users already exist.
 * Logins: admin / admin123 (ADMIN), agent / agent123 (AGENT).
 */
@Component
@ConditionalOnProperty(prefix = "skyroute.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private record Route(String flightNumber, String origin, String destination, LocalTime departs,
                         Duration duration, String aircraft, int economyFare) {
    }

    private static final List<Route> ROUTES = List.of(
            new Route("SR101", "DFW", "JFK", LocalTime.of(7, 0), Duration.ofMinutes(215), "B738", 189),
            new Route("SR103", "DFW", "JFK", LocalTime.of(13, 30), Duration.ofMinutes(220), "A321", 159),
            new Route("SR105", "DFW", "JFK", LocalTime.of(18, 45), Duration.ofMinutes(210), "B738", 219),
            new Route("SR202", "JFK", "LHR", LocalTime.of(21, 0), Duration.ofMinutes(415), "B789", 549),
            new Route("SR301", "DFW", "LAX", LocalTime.of(9, 15), Duration.ofMinutes(200), "A321", 149),
            new Route("SR404", "LAX", "NRT", LocalTime.of(11, 30), Duration.ofMinutes(700), "B77W", 899),
            new Route("SR501", "BLR", "DXB", LocalTime.of(4, 10), Duration.ofMinutes(245), "A320", 299),
            new Route("SR502", "DXB", "LHR", LocalTime.of(9, 40), Duration.ofMinutes(470), "A388", 459));

    private final AppUserRepository users;
    private final AirportRepository airports;
    private final FlightRepository flights;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DataSeeder(AppUserRepository users, AirportRepository airports, FlightRepository flights,
                      PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.airports = airports;
        this.flights = flights;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Seed data already present, skipping");
            return;
        }
        users.saveAll(List.of(
                new AppUser(null, "admin", encoder.encode("admin123"), Set.of(Role.ADMIN, Role.AGENT), true),
                new AppUser(null, "agent", encoder.encode("agent123"), Set.of(Role.AGENT), true)));
        airports.saveAll(List.of(
                new Airport("DFW", "Dallas/Fort Worth International", "Dallas", "US", "America/Chicago"),
                new Airport("JFK", "John F. Kennedy International", "New York", "US", "America/New_York"),
                new Airport("LAX", "Los Angeles International", "Los Angeles", "US", "America/Los_Angeles"),
                new Airport("LHR", "Heathrow", "London", "GB", "Europe/London"),
                new Airport("NRT", "Narita International", "Tokyo", "JP", "Asia/Tokyo"),
                new Airport("DXB", "Dubai International", "Dubai", "AE", "Asia/Dubai"),
                new Airport("BLR", "Kempegowda International", "Bengaluru", "IN", "Asia/Kolkata")));

        LocalDate today = LocalDate.now(clock);
        List<Flight> schedule = IntStream.rangeClosed(1, 30)
                .mapToObj(today::plusDays)
                .flatMap(day -> ROUTES.stream().map(r -> toFlight(r, day)))
                .toList();
        flights.saveAll(schedule);
        log.info("Seeded 2 users, 7 airports and {} flights", schedule.size());
    }

    private static Flight toFlight(Route r, LocalDate day) {
        var departure = day.atTime(r.departs()).toInstant(ZoneOffset.UTC);
        BigDecimal economy = BigDecimal.valueOf(r.economyFare());
        return new Flight(r.flightNumber(), r.origin(), r.destination(), departure,
                departure.plus(r.duration()), r.aircraft(), List.of(
                        new CabinInventory(CabinClass.ECONOMY, 150, 150, economy, "USD"),
                        new CabinInventory(CabinClass.PREMIUM_ECONOMY, 24, 24,
                                economy.multiply(BigDecimal.valueOf(1.8)), "USD"),
                        new CabinInventory(CabinClass.BUSINESS, 16, 16,
                                economy.multiply(BigDecimal.valueOf(4)), "USD")));
    }
}
