package com.codewalnut.prreviewer.controller;

import com.codewalnut.prreviewer.dto.PracticeRequest;
import com.codewalnut.prreviewer.dto.PracticeResponse;
import com.codewalnut.prreviewer.service.PracticeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practices")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping
    public List<PracticeResponse> list() {
        return practiceService.list().stream().map(PracticeResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PracticeResponse get(@PathVariable UUID id) {
        return PracticeResponse.from(practiceService.get(id));
    }

    @PostMapping
    public ResponseEntity<PracticeResponse> create(@Valid @RequestBody PracticeRequest request) {
        PracticeResponse response = PracticeResponse.from(practiceService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public PracticeResponse update(@PathVariable UUID id, @Valid @RequestBody PracticeRequest request) {
        return PracticeResponse.from(practiceService.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public PracticeResponse setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return PracticeResponse.from(practiceService.setActive(id, active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        practiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
