package com.skyroute.flight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skyroute.auth.JwtService;
import com.skyroute.auth.SecurityConfig;
import com.skyroute.common.config.SkyRouteProperties;
import com.skyroute.common.error.NotFoundException;
import com.skyroute.common.logging.CorrelationIdFilter;
import com.skyroute.flight.FlightDtos.FlightSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Web slice: controller + security + exception handler + filters, service mocked. */
@WebMvcTest(FlightController.class)
@Import({SecurityConfig.class, JwtService.class, CorrelationIdFilter.class})
@TestPropertySource(properties = {
        "skyroute.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMzI=",
        "skyroute.jwt.issuer=test", "skyroute.grpc.port=9090"})
@org.springframework.boot.context.properties.EnableConfigurationProperties(SkyRouteProperties.class)
class FlightControllerTest {

    @Autowired MockMvc mvc;
    @MockBean FlightService flightService;
    @MockBean UserDetailsService userDetailsService;

    @Test
    void searchIsPublicAndReturnsSummaries() throws Exception {
        when(flightService.search(any())).thenReturn(List.of(new FlightSummary("f1", "SR101", "DFW", "JFK",
                Instant.parse("2026-11-01T07:00:00Z"), Instant.parse("2026-11-01T10:35:00Z"), 215, "B738",
                FlightStatus.SCHEDULED, CabinClass.ECONOMY, 42, new BigDecimal("189.00"), "USD")));

        mvc.perform(get("/api/v1/flights/search")
                        .param("origin", "DFW").param("destination", "JFK").param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$[0].flightNumber").value("SR101"))
                .andExpect(jsonPath("$[0].fare").value(189.00));
    }

    @Test
    void invalidSearchReturnsProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/flights/search")
                        .param("origin", "DALLAS").param("destination", "JFK").param("date", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.origin").exists());
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        when(flightService.get("nope")).thenThrow(new NotFoundException("Flight", "nope"));

        mvc.perform(get("/api/v1/flights/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void statusChangeRequiresAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/flights/f1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELAYED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void statusChangeIsForbiddenForAgents() throws Exception {
        mvc.perform(patch("/api/v1/flights/f1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELAYED\"}"))
                .andExpect(status().isForbidden());
    }
}
