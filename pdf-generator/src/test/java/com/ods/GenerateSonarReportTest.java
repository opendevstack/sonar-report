package com.ods;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenerateSonarReportTest {

    @Test
    void parseArgs_allKnownFlags_returnsCorrectMap() {
        String[] args = {
            "--sonar-url", "http://sonar",
            "--token", "abc123",
            "--project", "myProj",
            "--branch", "main",
            "--output", "report.pdf"
        };
        Map<String, String> result = GenerateSonarReport.parseArgs(args);
        assertEquals("http://sonar", result.get("--sonar-url"));
        assertEquals("abc123", result.get("--token"));
        assertEquals("myProj", result.get("--project"));
        assertEquals("main", result.get("--branch"));
        assertEquals("report.pdf", result.get("--output"));
    }

    @Test
    void parseArgs_emptyArgs_returnsEmptyMap() {
        Map<String, String> result = GenerateSonarReport.parseArgs(new String[]{});
        assertTrue(result.isEmpty());
    }

    @Test
    void parseArgs_danglingFlag_isIgnored() {
        String[] args = {"--sonar-url", "http://sonar", "--token"};
        Map<String, String> result = GenerateSonarReport.parseArgs(args);
        assertEquals(1, result.size());
        assertEquals("http://sonar", result.get("--sonar-url"));
    }

    @Test
    void parseArgs_nonFlagArgument_isIgnored() {
        String[] args = {"notaflag", "value", "--token", "abc"};
        Map<String, String> result = GenerateSonarReport.parseArgs(args);
        assertEquals(1, result.size());
        assertEquals("abc", result.get("--token"));
    }

    @Test
    void parseArgs_duplicateFlag_lastValueWins() {
        String[] args = {"--token", "first", "--token", "second"};
        Map<String, String> result = GenerateSonarReport.parseArgs(args);
        assertEquals("second", result.get("--token"));
    }
}
