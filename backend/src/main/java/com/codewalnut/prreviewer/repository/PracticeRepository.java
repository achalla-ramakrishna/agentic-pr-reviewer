package com.codewalnut.prreviewer.repository;

import com.codewalnut.prreviewer.domain.Language;
import com.codewalnut.prreviewer.domain.Practice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeRepository extends JpaRepository<Practice, UUID> {

    List<Practice> findByLanguageAndActiveTrue(Language language);
}
