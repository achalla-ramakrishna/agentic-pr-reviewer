package com.skyroute.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyroute.booking.BookingDtos.Caller;
import com.skyroute.booking.BookingDtos.CreateBookingRequest;
import com.skyroute.booking.BookingDtos.SegmentRequest;
import com.skyroute.booking.BookingService;
import com.skyroute.booking.BookingStatus;
import com.skyroute.booking.Passenger;
import com.skyroute.booking.PassengerType;
import com.skyroute.flight.CabinClass;
import com.skyroute.flight.CabinInventory;
import com.skyroute.flight.Flight;
import com.skyroute.flight.FlightInventoryRepository;
import com.skyroute.flight.FlightRepository;
import com.skyroute.report.ReportService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full stack against a real MongoDB (Testcontainers starts mongo:7 in Docker).
 * Runs in `mvn verify`; silently skipped on machines without Docker.
 */
@SpringBootTest(properties = {"skyroute.seed.enabled=false", "skyroute.grpc.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class BookingFlowIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("skyroute-it"));
    }

    @Autowired MongoTemplate mongo;
    @Autowired FlightRepository flights;
    @Autowired FlightInventoryRepository inventory;
    @Autowired BookingService bookingService;
    @Autowired ReportService reportService;

    private Flight flight;

    @BeforeEach
    void setUp() {
        mongo.getDb().drop();
        Instant dep = Instant.now().plus(Duration.ofDays(3));
        flight = flights.save(new Flight("SR101", "DFW", "JFK", dep, dep.plus(Duration.ofHours(4)), "B738",
                List.of(new CabinInventory(CabinClass.ECONOMY, 10, 10, new BigDecimal("199.99"), "USD"))));
    }

    @Test
    void neverOversellsUnderConcurrentLoad() throws Exception {
        AtomicInteger successes = new AtomicInteger();
        // Java 17: ExecutorService isn't AutoCloseable yet (that's Java 19+), so shut down explicitly.
        var pool = Executors.newFixedThreadPool(16);
        IntStream.range(0, 50).forEach(i -> pool.submit(() -> {
            if (inventory.holdSeats(flight.getId(), CabinClass.ECONOMY, 1)) {
                successes.incrementAndGet();
            }
        }));
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(successes).hasValue(10);
        assertThat(flights.findById(flight.getId()).orElseThrow().cabin(CabinClass.ECONOMY).orElseThrow()
                .seatsAvailable()).isZero();
    }

    @Test
    void bookCancelAndReport() {
        var caller = new Caller("agent", false);
        var request = new CreateBookingRequest("t@example.com",
                List.of(new Passenger("Ana", "Diaz", PassengerType.ADT, null)),
                List.of(new SegmentRequest(flight.getId(), CabinClass.ECONOMY)));

        var booking = bookingService.create(request, caller);
        assertThat(booking.totalAmount()).isEqualByComparingTo("199.99");
        assertThat(seatsLeft()).isEqualTo(9);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var revenue = reportService.routeRevenue(today, today);
        assertThat(revenue).singleElement().satisfies(r -> {
            assertThat(r.origin()).isEqualTo("DFW");
            assertThat(r.revenue()).isEqualByComparingTo("199.99");
        });

        var cancelled = bookingService.cancel(booking.recordLocator(), caller);
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(seatsLeft()).isEqualTo(10);
    }

    private int seatsLeft() {
        return flights.findById(flight.getId()).orElseThrow().cabin(CabinClass.ECONOMY).orElseThrow().seatsAvailable();
    }
}
