package com.ods;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportBuilderTest {

    // --- extractComponent ---

    @Test
    void extractComponent_withColon_returnsAfterColon() {
        assertEquals("src/main/java/Foo.java", ReportBuilder.extractComponent("myproject:src/main/java/Foo.java"));
    }

    @Test
    void extractComponent_noColon_returnsFull() {
        assertEquals("myproject", ReportBuilder.extractComponent("myproject"));
    }

    @Test
    void extractComponent_multipleColons_splitsOnFirst() {
        assertEquals("path:to/file", ReportBuilder.extractComponent("project:path:to/file"));
    }

    // --- ratingToLetter ---

    @Test
    void ratingToLetter_allRatings() {
        assertEquals("A", ReportBuilder.ratingToLetter("1.0"));
        assertEquals("B", ReportBuilder.ratingToLetter("2.0"));
        assertEquals("C", ReportBuilder.ratingToLetter("3.0"));
        assertEquals("D", ReportBuilder.ratingToLetter("4.0"));
        assertEquals("E", ReportBuilder.ratingToLetter("5.0"));
    }

    @Test
    void ratingToLetter_unknownValue_throwsAssertionError() {
        assertThrows(AssertionError.class, () -> ReportBuilder.ratingToLetter("6.0"));
    }

    // --- minsToDaysHoursMins ---

    @Test
    void minsToDaysHoursMins_zero() {
        assertEquals("0d 00h 00m", ReportBuilder.minsToDaysHoursMins(0));
    }

    @Test
    void minsToDaysHoursMins_exactHour() {
        assertEquals("0d 01h 00m", ReportBuilder.minsToDaysHoursMins(60));
    }

    @Test
    void minsToDaysHoursMins_mixed() {
        assertEquals("0d 01h 30m", ReportBuilder.minsToDaysHoursMins(90));
    }

    @Test
    void minsToDaysHoursMins_exactDay() {
        assertEquals("1d 00h 00m", ReportBuilder.minsToDaysHoursMins(24 * 60));
    }

    @Test
    void minsToDaysHoursMins_largeValue() {
        assertEquals("2d 03h 45m", ReportBuilder.minsToDaysHoursMins(2 * 24 * 60 + 3 * 60 + 45));
    }

    // --- groupHotspotsByRule ---

    @Test
    void groupHotspotsByRule_singleHotspot_correctEntry() {
        JSONArray array = new JSONArray();
        array.put(buildHotspot("java:S1234", "Security issue", "HIGH", "project:src/Foo.java", 42));

        Map<String, JSONObject> result = ReportBuilder.groupHotspotsByRule(array);

        assertEquals(1, result.size());
        JSONObject entry = result.get("java:S1234");
        assertNotNull(entry);
        assertEquals(1, entry.getInt("count"));
        assertEquals("Security issue", entry.getString("message"));
        assertEquals("src/Foo.java: 42", entry.getString("location"));
    }

    @Test
    void groupHotspotsByRule_duplicateRuleKey_aggregatesCountAndLocations() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < 3; i++) {
            array.put(buildHotspot("java:S1234", "Security issue", "HIGH", "project:src/Foo.java", 10 + i));
        }

        Map<String, JSONObject> result = ReportBuilder.groupHotspotsByRule(array);

        assertEquals(1, result.size());
        assertEquals(3, result.get("java:S1234").getInt("count"));
        assertTrue(result.get("java:S1234").getString("location").contains(" | "));
    }

    @Test
    void groupHotspotsByRule_differentRuleKeys_separateEntries() {
        JSONArray array = new JSONArray();
        array.put(buildHotspot("java:S0001", "Issue A", "HIGH", "project:A.java", 1));
        array.put(buildHotspot("java:S0002", "Issue B", "LOW", "project:B.java", 2));

        Map<String, JSONObject> result = ReportBuilder.groupHotspotsByRule(array);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("java:S0001"));
        assertTrue(result.containsKey("java:S0002"));
    }

    // --- groupIssuesByRule ---

    @Test
    void groupIssuesByRule_singleIssueWithTextRange_correctEntry() {
        JSONArray array = new JSONArray();
        array.put(buildIssue("java:S2095", "Close this resource", "MAJOR", "BUG", "project:src/Bar.java", 99));

        Map<String, JSONObject> result = ReportBuilder.groupIssuesByRule(array);

        assertEquals(1, result.size());
        JSONObject entry = result.get("java:S2095");
        assertNotNull(entry);
        assertEquals(1, entry.getInt("count"));
        assertEquals("MAJOR", entry.getString("severity"));
        assertEquals("BUG", entry.getString("type"));
        assertEquals("src/Bar.java: 99", entry.getString("location"));
    }

    @Test
    void groupIssuesByRule_issueWithoutTextRange_usesFileOnly() {
        JSONObject issue = new JSONObject();
        issue.put("rule", "java:S0000");
        issue.put("message", "Some issue");
        issue.put("severity", "INFO");
        issue.put("type", "CODE_SMELL");
        issue.put("component", "project:src/Baz.java");

        JSONArray array = new JSONArray();
        array.put(issue);

        Map<String, JSONObject> result = ReportBuilder.groupIssuesByRule(array);

        assertEquals("src/Baz.java", result.get("java:S0000").getString("location"));
    }

    @Test
    void groupIssuesByRule_duplicateRuleKey_aggregatesCount() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < 4; i++) {
            array.put(buildIssue("java:S9999", "Repeated", "MINOR", "CODE_SMELL", "project:X.java", 5 + i));
        }

        Map<String, JSONObject> result = ReportBuilder.groupIssuesByRule(array);

        assertEquals(1, result.size());
        assertEquals(4, result.get("java:S9999").getInt("count"));
    }

    // --- helpers ---

    private JSONObject buildHotspot(String ruleKey, String message, String probability, String component, int line) {
        JSONObject hotspot = new JSONObject();
        hotspot.put("ruleKey", ruleKey);
        hotspot.put("message", message);
        hotspot.put("vulnerabilityProbability", probability);
        hotspot.put("component", component);
        JSONObject textRange = new JSONObject();
        textRange.put("startLine", line);
        hotspot.put("textRange", textRange);
        return hotspot;
    }

    private JSONObject buildIssue(String rule, String message, String severity, String type, String component, int line) {
        JSONObject issue = new JSONObject();
        issue.put("rule", rule);
        issue.put("message", message);
        issue.put("severity", severity);
        issue.put("type", type);
        issue.put("component", component);
        JSONObject textRange = new JSONObject();
        textRange.put("startLine", line);
        issue.put("textRange", textRange);
        return issue;
    }
}
