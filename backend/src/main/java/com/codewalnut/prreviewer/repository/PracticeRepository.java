package com.codewalnut.prreviewer.repository;

import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Technology;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeRepository extends JpaRepository<Practice, UUID> {

    List<Practice> findByTechnologyAndActiveTrue(Technology technology);

    List<Practice> findByPracticeCodeIn(Collection<String> practiceCodes);
}
