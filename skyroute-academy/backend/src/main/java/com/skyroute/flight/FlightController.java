package com.skyroute.flight;

import com.skyroute.flight.FlightDtos.CreateFlightRequest;
import com.skyroute.flight.FlightDtos.FlightSummary;
import com.skyroute.flight.FlightDtos.UpdateFareRequest;
import com.skyroute.flight.FlightDtos.UpdateStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    /** GET /api/v1/flights/search?origin=DFW&destination=JFK&date=2026-11-01&cabin=ECONOMY&passengers=2&sort=FARE */
    @GetMapping("/search")
    public List<FlightSummary> search(@Valid FlightSearchCriteria criteria) {
        return flightService.search(criteria);
    }

    @GetMapping("/{id}")
    public Flight get(@PathVariable String id) {
        return flightService.get(id);
    }

    @PostMapping
    public ResponseEntity<Flight> create(@Valid @RequestBody CreateFlightRequest request) {
        Flight created = flightService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/flights/" + created.getId())).body(created);
    }

    @PatchMapping("/{id}/status")
    public Flight updateStatus(@PathVariable String id, @Valid @RequestBody UpdateStatusRequest request) {
        return flightService.updateStatus(id, request.status());
    }

    @PatchMapping("/{id}/cabins/{cabin}/fare")
    public Flight updateFare(@PathVariable String id, @PathVariable CabinClass cabin,
                             @Valid @RequestBody UpdateFareRequest request) {
        return flightService.updateFare(id, cabin, request.baseFare());
    }
}
