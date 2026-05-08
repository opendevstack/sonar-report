package com.ods;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PDFReportWriterTest {

    // --- getCurrentGMTTimeFormatted ---

    @Test
    void getCurrentGMTTimeFormatted_matchesExpectedPattern() {
        String result = PDFReportWriter.getCurrentGMTTimeFormatted();
        assertNotNull(result);
        // Expected: "May 7, 2026, 09:30 AM GMT"
        Pattern pattern = Pattern.compile("^[A-Z][a-z]+ \\d{1,2}, \\d{4}, \\d{2}:\\d{2} (AM|PM) GMT$");
        assertTrue(pattern.matcher(result).matches(),
            "Date string '" + result + "' does not match expected format");
    }

    @Test
    void getCurrentGMTTimeFormatted_containsGMT() {
        assertTrue(PDFReportWriter.getCurrentGMTTimeFormatted().endsWith("GMT"));
    }

    // --- splitBySlash ---

    @Test
    void splitBySlash_noSlash_returnsSingleElement() {
        List<String> result = PDFReportWriter.splitBySlash("filename.java");
        assertEquals(1, result.size());
        assertEquals("filename.java", result.get(0));
    }

    @Test
    void splitBySlash_singleSlash_splitsThere() {
        List<String> result = PDFReportWriter.splitBySlash("src/main");
        assertEquals(2, result.size());
        assertEquals("src/", result.get(0));
        assertEquals("main", result.get(1));
    }

    @Test
    void splitBySlash_multipleSlashes_splitsAtEach() {
        List<String> result = PDFReportWriter.splitBySlash("src/main/java/");
        assertEquals(3, result.size());
        assertEquals("src/", result.get(0));
        assertEquals("main/", result.get(1));
        assertEquals("java/", result.get(2));
    }

    @Test
    void splitBySlash_emptyString_returnsEmptyElement() {
        List<String> result = PDFReportWriter.splitBySlash("");
        assertEquals(0, result.size());
    }
}
