package com.codewalnut.prreviewer.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rebuilds the "new file" view of a changed file so the rule engine can run
 * a regex across it exactly as it would against whole-file content --
 * including patterns that span multiple lines -- while still knowing which
 * reconstructed line is a genuinely added line versus pre-existing context.
 *
 * For whole-repo mode (fromWholeFileContent), every line is "added": there's
 * no diff, so the entire file is in scope. For PR mode (fromPatch), only
 * '+' lines are added; '-' lines are dropped entirely since they don't exist
 * in the new file, and unprefixed context (' ') lines are kept for matching
 * purposes but marked not-added, so the rule engine can require that a match
 * actually touches changed code rather than flagging pre-existing lines
 * GitHub only included for readability.
 */
public final class UnifiedDiffReconstructor {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    private UnifiedDiffReconstructor() {}

    public record ReconstructedLine(String text, int newLineNumber, boolean added) {}

    public static final class Reconstructed {
        private final String text;
        private final List<ReconstructedLine> lines;
        private final int[] lineStartOffsets;

        private Reconstructed(String text, List<ReconstructedLine> lines) {
            this.text = text;
            this.lines = lines;
            this.lineStartOffsets = new int[lines.size()];
            int offset = 0;
            for (int i = 0; i < lines.size(); i++) {
                lineStartOffsets[i] = offset;
                offset += lines.get(i).text().length() + 1; // +1 for the '\n' joiner
            }
        }

        public String text() {
            return text;
        }

        public List<ReconstructedLine> lines() {
            return lines;
        }

        /** The reconstructed line covering a given character offset into text(). */
        public ReconstructedLine lineAt(int offset) {
            if (lines.isEmpty()) {
                throw new IllegalStateException("no lines to map offset " + offset + " against");
            }
            int idx = Arrays.binarySearch(lineStartOffsets, offset);
            if (idx < 0) {
                idx = -idx - 2;
            }
            idx = Math.max(0, Math.min(idx, lines.size() - 1));
            return lines.get(idx);
        }

        public boolean anyAddedBetween(int fromLine, int toLine) {
            for (ReconstructedLine line : lines) {
                if (line.newLineNumber() >= fromLine && line.newLineNumber() <= toLine && line.added()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Reconstructed fromWholeFileContent(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n");
        String[] rawLines = normalized.split("\n", -1);
        List<ReconstructedLine> lines = new ArrayList<>(rawLines.length);
        for (int i = 0; i < rawLines.length; i++) {
            lines.add(new ReconstructedLine(rawLines[i], i + 1, true));
        }
        return new Reconstructed(String.join("\n", rawLines), lines);
    }

    public static Reconstructed fromPatch(String patch) {
        if (patch == null || patch.isBlank()) {
            return new Reconstructed("", Collections.emptyList());
        }

        List<ReconstructedLine> lines = new ArrayList<>();
        int newLineNumber = 0;
        for (String rawLine : patch.replace("\r\n", "\n").split("\n", -1)) {
            if (rawLine.startsWith("@@")) {
                Matcher header = HUNK_HEADER.matcher(rawLine);
                if (header.find()) {
                    newLineNumber = Integer.parseInt(header.group(1)) - 1;
                }
                continue;
            }
            if (rawLine.startsWith("\\")) {
                continue; // "\ No newline at end of file"
            }
            if (rawLine.isEmpty()) {
                continue; // trailing artifact of the final split, not a real line
            }

            char marker = rawLine.charAt(0);
            if (marker == '-') {
                continue; // removed line: not part of the new file
            }
            String content = rawLine.length() > 1 ? rawLine.substring(1) : "";
            newLineNumber++;
            lines.add(new ReconstructedLine(content, newLineNumber, marker == '+'));
        }

        String text = lines.stream().map(ReconstructedLine::text).collect(Collectors.joining("\n"));
        return new Reconstructed(text, lines);
    }
}
