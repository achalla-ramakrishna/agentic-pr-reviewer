package com.skyroute.booking;

import com.skyroute.booking.BookingDtos.BookingView;
import com.skyroute.booking.BookingDtos.Caller;
import com.skyroute.booking.BookingDtos.CreateBookingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingView> create(@Valid @RequestBody CreateBookingRequest request, Authentication auth) {
        BookingView created = bookingService.create(request, caller(auth));
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + created.recordLocator())).body(created);
    }

    @GetMapping
    public List<BookingView> mine(@RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                  Authentication auth) {
        return bookingService.listMine(caller(auth), page, size);
    }

    @GetMapping("/{recordLocator}")
    public BookingView get(@PathVariable String recordLocator, Authentication auth) {
        return bookingService.get(recordLocator, caller(auth));
    }

    /** POST, not DELETE: cancelling is a state change, the booking record is kept for audit. */
    @PostMapping("/{recordLocator}/cancel")
    public BookingView cancel(@PathVariable String recordLocator, Authentication auth) {
        return bookingService.cancel(recordLocator, caller(auth));
    }

    private static Caller caller(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new Caller(auth.getName(), admin);
    }
}
