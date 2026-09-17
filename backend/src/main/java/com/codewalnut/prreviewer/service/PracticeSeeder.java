package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds the practices knowledge base from
 * classpath:practices/java-fullstack-practices.json on first boot only
 * (guarded by an empty-table check). The JSON is organized the way it's
 * curated: technology -> topic (a subcategory + its Category) -> a list of
 * bad practices. This class just flattens that into Practice rows.
 *
 * Practices are meant to be edited afterward through the CRUD API/dashboard
 * (or by editing the JSON and re-seeding a fresh environment) — this is a
 * starting set, not something re-applied on every restart.
 */
@Component
public class PracticeSeeder implements ApplicationRunner {

    private static final String PRACTICES_RESOURCE = "practices/java-fullstack-practices.json";

    private final PracticeRepository practiceRepository;
    private final ObjectMapper objectMapper;

    public PracticeSeeder(PracticeRepository practiceRepository, ObjectMapper objectMapper) {
        this.practiceRepository = practiceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (practiceRepository.count() > 0) {
            return;
        }
        practiceRepository.saveAll(loadPractices());
    }

    private List<Practice> loadPractices() throws IOException {
        PracticesFile file;
        try (InputStream in = new ClassPathResource(PRACTICES_RESOURCE).getInputStream()) {
            file = objectMapper.readValue(in, PracticesFile.class);
        }

        List<Practice> practices = new ArrayList<>();
        for (TechnologyGroup group : file.technologies()) {
            for (Topic topic : group.topics()) {
                for (PracticeSeed seed : topic.practices()) {
                    practices.add(
                            Practice.builder()
                                    .practiceCode(seed.id())
                                    .title(seed.title())
                                    .description(seed.description())
                                    .category(topic.category())
                                    .subcategory(topic.name())
                                    .severity(seed.severity())
                                    .technology(group.technology())
                                    .code(seed.code())
                                    .solution(seed.solution())
                                    .risk(seed.risk())
                                    .detectionPattern(seed.detectionPattern())
                                    .build());
                }
            }
        }
        return practices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PracticesFile(List<TechnologyGroup> technologies) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TechnologyGroup(Technology technology, List<Topic> topics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Topic(String name, Category category, List<PracticeSeed> practices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PracticeSeed(
            String id,
            String title,
            Severity severity,
            String description,
            String risk,
            String code,
            String solution,
            String detectionPattern) {}
}
