package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.dto.PracticeRequest;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PracticeService {

    private final PracticeRepository practiceRepository;

    public PracticeService(PracticeRepository practiceRepository) {
        this.practiceRepository = practiceRepository;
    }

    public List<Practice> list() {
        return practiceRepository.findAll();
    }

    /** Used by the rule engine (chunk 4) and LLM context builder (chunk 5) to scope by technology. */
    public List<Practice> listActiveByTechnology(Technology technology) {
        return practiceRepository.findByTechnologyAndActiveTrue(technology);
    }

    public Practice get(UUID id) {
        return practiceRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Practice not found: " + id));
    }

    public Practice create(PracticeRequest request) {
        Practice practice =
                Practice.builder()
                        .practiceCode(request.practiceCode())
                        .title(request.title())
                        .description(request.description())
                        .category(request.category())
                        .subcategory(request.subcategory())
                        .severity(request.severity())
                        .technology(request.technology())
                        .code(request.code())
                        .solution(request.solution())
                        .risk(request.risk())
                        .detectionPattern(request.detectionPattern())
                        .build();
        return practiceRepository.save(practice);
    }

    public Practice update(UUID id, PracticeRequest request) {
        Practice practice = get(id);
        practice.setPracticeCode(request.practiceCode());
        practice.setTitle(request.title());
        practice.setDescription(request.description());
        practice.setCategory(request.category());
        practice.setSubcategory(request.subcategory());
        practice.setSeverity(request.severity());
        practice.setTechnology(request.technology());
        practice.setCode(request.code());
        practice.setSolution(request.solution());
        practice.setRisk(request.risk());
        practice.setDetectionPattern(request.detectionPattern());
        return practiceRepository.save(practice);
    }

    public Practice setActive(UUID id, boolean active) {
        Practice practice = get(id);
        practice.setActive(active);
        return practiceRepository.save(practice);
    }

    public void delete(UUID id) {
        if (!practiceRepository.existsById(id)) {
            throw new NotFoundException("Practice not found: " + id);
        }
        practiceRepository.deleteById(id);
    }
}
