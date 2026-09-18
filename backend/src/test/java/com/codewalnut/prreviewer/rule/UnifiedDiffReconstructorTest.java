package com.codewalnut.prreviewer.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.rule.UnifiedDiffReconstructor.Reconstructed;
import com.codewalnut.prreviewer.rule.UnifiedDiffReconstructor.ReconstructedLine;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedDiffReconstructorTest {

    @Test
    void reconstructsWholeFileContentWithEveryLineMarkedAdded() {
        Reconstructed reconstructed = UnifiedDiffReconstructor.fromWholeFileContent("line1\nline2\nline3");

        List<ReconstructedLine> lines = reconstructed.lines();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(new ReconstructedLine("line1", 1, true));
        assertThat(lines.get(1)).isEqualTo(new ReconstructedLine("line2", 2, true));
        assertThat(lines.get(2)).isEqualTo(new ReconstructedLine("line3", 3, true));
        assertThat(reconstructed.anyAddedBetween(1, 3)).isTrue();
    }

    @Test
    void reconstructsPatchDroppingRemovedLinesAndTrackingNewLineNumbers() {
        String patch =
                "@@ -10,4 +10,5 @@\n"
                        + " public void process() {\n"
                        + "-    doSomething();\n"
                        + "+    doSomethingElse();\n"
                        + "+    extra();\n"
                        + " }\n";

        Reconstructed reconstructed = UnifiedDiffReconstructor.fromPatch(patch);

        List<ReconstructedLine> lines = reconstructed.lines();
        assertThat(lines)
                .containsExactly(
                        new ReconstructedLine("public void process() {", 10, false),
                        new ReconstructedLine("    doSomethingElse();", 11, true),
                        new ReconstructedLine("    extra();", 12, true),
                        new ReconstructedLine("}", 13, false));
    }

    @Test
    void anyAddedBetweenOnlyTrueWhenAnAddedLineFallsInRange() {
        String patch = "@@ -10,4 +10,5 @@\n" + " context();\n" + "+added();\n" + " moreContext();\n";

        Reconstructed reconstructed = UnifiedDiffReconstructor.fromPatch(patch);

        assertThat(reconstructed.anyAddedBetween(10, 10)).isFalse(); // context-only range
        assertThat(reconstructed.anyAddedBetween(11, 11)).isTrue(); // the added line itself
        assertThat(reconstructed.anyAddedBetween(10, 12)).isTrue(); // range spanning the added line
    }

    @Test
    void lineAtMapsACharacterOffsetBackToItsReconstructedLine() {
        String patch = "@@ -1,2 +1,3 @@\n" + " public void process() {\n" + "+    extra();\n" + " }\n";

        Reconstructed reconstructed = UnifiedDiffReconstructor.fromPatch(patch);
        int offset = reconstructed.text().indexOf("extra");

        assertThat(reconstructed.lineAt(offset).newLineNumber()).isEqualTo(2);
        assertThat(reconstructed.lineAt(offset).added()).isTrue();
    }

    @Test
    void skipsNoNewlineMarkerLineWithoutAffectingNumbering() {
        String patch = "@@ -1,1 +1,1 @@\n" + "+last line\n" + "\\ No newline at end of file\n";

        Reconstructed reconstructed = UnifiedDiffReconstructor.fromPatch(patch);

        assertThat(reconstructed.lines()).containsExactly(new ReconstructedLine("last line", 1, true));
    }

    @Test
    void blankOrNullPatchReconstructsToEmpty() {
        assertThat(UnifiedDiffReconstructor.fromPatch(null).lines()).isEmpty();
        assertThat(UnifiedDiffReconstructor.fromPatch("  ").lines()).isEmpty();
    }
}
