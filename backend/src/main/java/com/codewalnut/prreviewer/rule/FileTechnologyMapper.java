package com.codewalnut.prreviewer.rule;

import com.codewalnut.prreviewer.domain.Technology;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Maps a changed file's path to the Practice.technology buckets whose
 * detection patterns are worth running against it. GENERAL practices
 * (hardcoded secrets, unpinned dependencies, assertion-free tests) apply to
 * every file regardless of extension, so they're always included.
 */
public final class FileTechnologyMapper {

    private FileTechnologyMapper() {}

    public static Set<Technology> technologiesFor(String filePath) {
        Set<Technology> technologies = EnumSet.of(Technology.GENERAL);
        if (filePath == null) {
            return technologies;
        }

        String lower = filePath.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot) : "";

        switch (extension) {
            case ".java" -> technologies.addAll(Set.of(Technology.JAVA, Technology.SPRING, Technology.HIBERNATE));
            case ".sql" -> technologies.addAll(Set.of(Technology.SQL, Technology.MYSQL));
            case ".js" -> technologies.add(Technology.JAVASCRIPT);
            case ".jsx" -> technologies.addAll(
                    Set.of(Technology.JAVASCRIPT, Technology.REACT, Technology.BOOTSTRAP));
            case ".ts" -> technologies.add(Technology.TYPESCRIPT);
            case ".tsx" -> technologies.addAll(
                    Set.of(Technology.TYPESCRIPT, Technology.REACT, Technology.BOOTSTRAP));
            case ".html", ".htm" -> technologies.addAll(Set.of(Technology.HTML, Technology.BOOTSTRAP));
            case ".css", ".scss" -> technologies.addAll(Set.of(Technology.CSS, Technology.BOOTSTRAP));
            default -> {
                // No language-specific bucket for this extension; GENERAL still applies.
            }
        }
        return technologies;
    }
}
