package com.skyroute.airport;

import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airports")
@Validated
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @GetMapping
    public List<Airport> search(@RequestParam("q") @Size(min = 1, max = 40) String query) {
        return airportService.search(query);
    }

    @GetMapping("/{code}")
    public Airport get(@PathVariable @Size(min = 3, max = 3) String code) {
        return airportService.get(code);
    }
}
