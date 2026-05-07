package com.ods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReportBuilder {

    private final SonarApiClient client;
    private final PDFReportWriter pdf;
    private final String apiUrl;
    private final String project;

    private static final Map<String, String> CATEGORY_NAMES = new HashMap<>();
    private static final Map<Integer, String> RATING_LABELS = new HashMap<>();

    static {
        CATEGORY_NAMES.put("buffer-overflow", "Buffer Overflow");
        CATEGORY_NAMES.put("sql-injection", "SQL Injection");
        CATEGORY_NAMES.put("rce", "Code Injection (RCE)");
        CATEGORY_NAMES.put("object-injection", "Object Injection");
        CATEGORY_NAMES.put("command-injection", "Command Injection");
        CATEGORY_NAMES.put("path-traversal-injection", "Path Traversal Injection");
        CATEGORY_NAMES.put("ldap-injection", "LDAP Injection");
        CATEGORY_NAMES.put("xpath-injection", "XPath Injection");
        CATEGORY_NAMES.put("log-injection", "Log Injection");
        CATEGORY_NAMES.put("xxe", "XML External Entity(XXE)");
        CATEGORY_NAMES.put("xss", "Cross-Site Scripting (XSS)");
        CATEGORY_NAMES.put("dos", "Denial of Service (DoS)");
        CATEGORY_NAMES.put("ssrf", "Server-Side Request Forgery (SSRF)");
        CATEGORY_NAMES.put("csrf", "Cross-Site Request Forgery (CSRF)");
        CATEGORY_NAMES.put("http-response-splitting", "HTTP Response Splitting");
        CATEGORY_NAMES.put("open-redirect", "Open Redirect");
        CATEGORY_NAMES.put("weak-cryptography", "Weak Cryptography");
        CATEGORY_NAMES.put("auth", "Authentication");
        CATEGORY_NAMES.put("insecure-conf", "Insecure Configuration");
        CATEGORY_NAMES.put("file-manipulation", "File Manipulation");
        CATEGORY_NAMES.put("encrypt-data", "Encryption of Sensitive Data");
        CATEGORY_NAMES.put("traceability", "Traceability");
        CATEGORY_NAMES.put("permission", "Permission");
        CATEGORY_NAMES.put("others", "Others");

        RATING_LABELS.put(1, "[A]");
        RATING_LABELS.put(2, "[B]");
        RATING_LABELS.put(3, "[C]");
        RATING_LABELS.put(4, "[D]");
        RATING_LABELS.put(5, "[E]");
    }

    public ReportBuilder(String apiUrl, String authToken, String project) throws IOException {
        this.client = new SonarApiClient(apiUrl, authToken);
        this.pdf = new PDFReportWriter();
        this.apiUrl = apiUrl;
        this.project = project;
    }

    public void build(String outputFile) throws IOException {
        buildIntroductionAndConfiguration();
        buildSynthesisSection();
        buildSecurityHotspotsSection();
        buildIssuesSection();
        pdf.insertIndexAtBeginning();
        pdf.addCoverPage("SonarQube Report", "Generated for " + project);
        pdf.save(outputFile);
    }

    private void buildIntroductionAndConfiguration() throws IOException {
        JSONObject data = null;
        try {
            data = client.fetchDataFromURL("/api/navigation/component?component=", project);
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        String name = data.getString("name");

        pdf.tittle2Font();
        pdf.addLine("INTRODUCTION");
        pdf.bodyFont();
        pdf.addLine("• This document contains results of the code analysis of " + name + ".");
        pdf.addLine("• Branch: " + data.getString("branch"));
        pdf.addLine("• Date: " + data.getString("analysisDate").replace("T", " "));

        pdf.tittle2Font();
        pdf.addLine("CONFIGURATION");

        String qualityProfiles = "• Quality Profiles: ";
        JSONArray qualityProfilesList = data.getJSONArray("qualityProfiles");
        for (int i = 0; i < qualityProfilesList.length(); i++) {
            JSONObject qp = qualityProfilesList.getJSONObject(i);
            String suffix = (i + 1 < qualityProfilesList.length()) ? ", " : ".";
            qualityProfiles += qp.getString("name") + " [" + qp.getString("language") + "]" + suffix;
        }

        pdf.bodyFont();
        pdf.addLine(qualityProfiles);
        pdf.addLine("• Quality Gate: " + data.getJSONObject("qualityGate").getString("name") + ".");
    }

    private void buildSynthesisSection() throws IOException {
        pdf.tittle2Font();
        pdf.addLine("SYNTHESIS");

        // Analysis Status
        pdf.tittle3Font();
        pdf.addLine("ANALYSIS STATUS");

        JSONObject data = null;
        try {
            data = client.fetchDataFromURL("/api/measures/component?metricKeys=reliability_rating,software_quality_maintainability_rating,security_rating,security_review_rating&component=", project);
            data = data.getJSONObject("component");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        String[] headers = {"Reliability", "Security", "Security Review", "Maintainability"};
        List<String[]> rows = new ArrayList<>();
        JSONArray measuresList = data.getJSONArray("measures");
        String[] measures = new String[4];
        for (int i = 0; i < measuresList.length(); i++) {
            measures[i] = ratingToLetter(measuresList.getJSONObject(i).getString("value"));
        }
        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // Quality Gate Status
        pdf.tittle3Font();
        pdf.addLine("QUALITY GATE STATUS");

        try {
            data = client.fetchDataFromURL("/api/qualitygates/project_status?projectKey=", project);
            data = data.getJSONObject("projectStatus");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        pdf.bodyFont();
        pdf.addLine("| Quality Gate Status | " + data.getString("status") + " |");

        // Metrics
        pdf.tittle3Font();
        pdf.addLine("METRICS");

        try {
            data = client.fetchDataFromURL("/api/measures/component?metricKeys=duplicated_lines_density,comment_lines_density,ncloc,complexity,cognitive_complexity,coverage&component=", project);
            data = data.getJSONObject("component");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        headers = new String[]{"Coverage", "Duplications", "Comment Density", "Lines of Code", "Cyclomatic Complexity", "Cognitive Complexity"};
        rows = new ArrayList<>();

        Map<String, Integer> metricIndex = new HashMap<>();
        metricIndex.put("coverage", 0);
        metricIndex.put("duplicated_lines_density", 1);
        metricIndex.put("comment_lines_density", 2);
        metricIndex.put("ncloc", 3);
        metricIndex.put("complexity", 4);
        metricIndex.put("cognitive_complexity", 5);

        measuresList = data.getJSONArray("measures");
        measures = new String[6];
        Arrays.fill(measures, "0");

        for (int i = 0; i < measuresList.length(); i++) {
            JSONObject measure = measuresList.getJSONObject(i);
            String metric = measure.getString("metric");
            String value = measure.getString("value");
            if (metricIndex.containsKey(metric)) {
                int index = metricIndex.get(metric);
                measures[index] = (metric.contains("density") || metric.equals("coverage")) ? value + "%" : value;
            }
        }

        int totalLinesOfCode = Integer.parseInt(measures[3]);
        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // Tests
        pdf.tittle3Font();
        pdf.addLine("TESTS");

        try {
            data = client.fetchDataFromURL("/api/measures/component?metricKeys=tests,test_success_density,skipped_tests,test_errors,test_failures&component=", project);
            data = data.getJSONObject("component");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        headers = new String[]{"Total", "Success Rate", "Skipped", "Errors", "Failures"};
        rows = new ArrayList<>();

        metricIndex = new HashMap<>();
        metricIndex.put("tests", 0);
        metricIndex.put("test_success_density", 1);
        metricIndex.put("skipped_tests", 2);
        metricIndex.put("test_errors", 3);
        metricIndex.put("test_failures", 4);

        measuresList = data.getJSONArray("measures");
        measures = new String[5];
        Arrays.fill(measures, "0");
        measures[1] = "0%";

        for (int i = 0; i < measuresList.length(); i++) {
            JSONObject measure = measuresList.getJSONObject(i);
            String metric = measure.getString("metric");
            String value = measure.getString("value");
            if (metricIndex.containsKey(metric)) {
                int index = metricIndex.get(metric);
                measures[index] = metric.equals("test_success_density") ? value + "%" : value;
            }
        }

        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // Technical Debt
        pdf.tittle3Font();
        pdf.addLine("DETAILED TECHNICAL DEBTS");

        try {
            data = client.fetchDataFromURL("/api/measures/component?metricKeys=reliability_remediation_effort,security_remediation_effort,sqale_index&component=", project);
            data = data.getJSONObject("component");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        headers = new String[]{"Reliability", "Security", "Maintainability", "Total"};
        rows = new ArrayList<>();

        metricIndex = new HashMap<>();
        metricIndex.put("reliability_remediation_effort", 0);
        metricIndex.put("security_remediation_effort", 1);
        metricIndex.put("sqale_index", 2);

        measuresList = data.getJSONArray("measures");
        measures = new String[4];
        Arrays.fill(measures, "0d 0h 0m");
        int totalMins = 0;

        for (int i = 0; i < measuresList.length(); i++) {
            JSONObject measure = measuresList.getJSONObject(i);
            String metric = measure.getString("metric");
            String value = measure.getString("value");
            if (metricIndex.containsKey(metric)) {
                int index = metricIndex.get(metric);
                int minutes = Integer.parseInt(value);
                totalMins += minutes;
                measures[index] = minsToDaysHoursMins(minutes);
            }
        }

        measures[3] = minsToDaysHoursMins(totalMins);
        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // Lines per Language
        pdf.tittle3Font();
        pdf.addLine("LINES PER LANGUAGE");

        try {
            data = client.fetchDataFromURL("/api/measures/component?metricKeys=ncloc_language_distribution&component=", project);
            data = data.getJSONObject("component");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        headers = new String[]{"Language", "Number of Lines", "Total Percent"};
        rows = new ArrayList<>();

        String rawLanguages = data.getJSONArray("measures").getJSONObject(0).getString("value");
        for (String pair : rawLanguages.split(";")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                int lines = Integer.parseInt(parts[1]);
                String percent = String.format("%.2f%%", (lines * 100.0) / totalLinesOfCode);
                rows.add(new String[]{parts[0], parts[1], percent});
            }
        }

        pdf.drawTable(500, headers, rows);
    }

    private void buildSecurityHotspotsSection() throws IOException {
        pdf.tittle2Font();
        pdf.addLine("SECURITY HOTSPOTS");
        pdf.tittle3Font();
        pdf.addLine("SECURITY HOTSPOTS COUNT BY CATEGORY AND PRIORITY");

        JSONObject data = null;
        JSONArray dataArray = null;
        try {
            data = client.fetchDataFromURL("/api/security_reports/show?standard=sonarsourceSecurity&project=", project);
            dataArray = data.getJSONArray("categories");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        String[] headers = {"Categories", "Security", "Security Hotspots"};
        List<String[]> rows = new ArrayList<>();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject object = dataArray.getJSONObject(i);
            String category = object.getString("category");
            int vuls = object.getInt("vulnerabilities");
            int vulsRate = object.has("vulnerabilityRating") ? object.getInt("vulnerabilityRating") : 1;
            int hotSpots = object.getInt("toReviewSecurityHotspots");
            int hotSpotsRate = object.getInt("securityReviewRating");
            rows.add(new String[]{
                CATEGORY_NAMES.get(category),
                vuls + "  " + RATING_LABELS.get(vulsRate),
                hotSpots + "  " + RATING_LABELS.get(hotSpotsRate)
            });
        }

        pdf.drawTable(500, headers, rows);

        pdf.tittle3Font();
        pdf.addLine("SECURITY HOTSPOT LIST");

        try {
            int pageIndex = 1;
            int total = Integer.MAX_VALUE;
            dataArray = new JSONArray();
            while ((pageIndex - 1) * 500 < total) {
                data = client.fetchDataFromURL(
                    String.format("/api/hotspots/search?status=TO_REVIEW&ps=500&pageIndex=%d&project=", pageIndex),
                    project
                );
                JSONArray currentPage = data.getJSONArray("hotspots");
                for (int i = 0; i < currentPage.length(); i++) {
                    dataArray.put(currentPage.getJSONObject(i));
                }
                if (data.has("paging")) {
                    total = data.getJSONObject("paging").getInt("total");
                }
                pageIndex++;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray hotspotArray = new JSONArray(groupHotspotsByRule(dataArray).values());
        for (int i = 0; i < hotspotArray.length(); i++) {
            JSONObject hotspot = hotspotArray.getJSONObject(i);
            pdf.startBulletEntry(hotspot.getString("message"));
            pdf.addIndentedLine("Vulnerability Probability", hotspot.getString("vulnerabilityProbability"));
            pdf.addIndentedLine("Count", Integer.toString(hotspot.getInt("count")));
            pdf.addIndentedLine("Locations", hotspot.getString("location"));
            pdf.addIndentedHyperlink("Root Cause/How to fix",
                apiUrl + "coding_rules?q=" + hotspot.getString("ruleKey") + "&open=" + hotspot.getString("ruleKey"),
                hotspot.getString("ruleKey"));
        }
    }

    private void buildIssuesSection() throws IOException {
        pdf.tittle2Font();
        pdf.addLine("ISSUES");
        pdf.tittle3Font();
        pdf.addLine("ISSUES COUNT BY SEVERITY AND TYPES");

        String[] headers = {"Type / Severity", "INFO", "MINOR", "MAJOR", "CRITICAL", "BLOCKER"};
        List<String[]> rows = new ArrayList<>();

        String[][] typeLabels = {{"BUG", "Bug"}, {"VULNERABILITY", "Vulnerability"}, {"CODE_SMELL", "Code Smell"}};
        for (String[] typeLabel : typeLabels) {
            JSONObject data = null;
            try {
                data = client.fetchDataFromURL("/api/issues/search?types=" + typeLabel[0] + "&facets=severities&componentKeys=", project);
            } catch (IOException | InterruptedException e) {
                System.err.println("Error at doing the HTTP petition: " + e.getMessage());
            }
            JSONArray facetValues = data.getJSONArray("facets").getJSONObject(0).getJSONArray("values");
            rows.add(new String[]{
                typeLabel[1],
                String.valueOf(facetValues.getJSONObject(4).getInt("count")),
                String.valueOf(facetValues.getJSONObject(0).getInt("count")),
                String.valueOf(facetValues.getJSONObject(1).getInt("count")),
                String.valueOf(facetValues.getJSONObject(2).getInt("count")),
                String.valueOf(facetValues.getJSONObject(3).getInt("count"))
            });
        }

        pdf.drawTable(500, headers, rows);

        pdf.tittle3Font();
        pdf.addLine("ISSUES LIST");

        JSONArray dataArray = new JSONArray();
        try {
            int pageIndex = 1;
            int total = Integer.MAX_VALUE;
            while ((pageIndex - 1) * 500 < total) {
                JSONObject data = client.fetchDataFromURL(
                    String.format("/api/issues/search?issueStatuses=OPEN&ps=500&pageIndex=%d&componentKeys=", pageIndex),
                    project
                );
                JSONArray currentPage = data.getJSONArray("issues");
                for (int i = 0; i < currentPage.length(); i++) {
                    dataArray.put(currentPage.getJSONObject(i));
                }
                if (data.has("paging")) {
                    total = data.getJSONObject("paging").getInt("total");
                }
                pageIndex++;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray issuesArray = new JSONArray(groupIssuesByRule(dataArray).values());
        for (int i = 0; i < issuesArray.length(); i++) {
            JSONObject issue = issuesArray.getJSONObject(i);
            pdf.startBulletEntry(issue.getString("message"));
            pdf.addIndentedLine("Type", issue.getString("type"));
            pdf.addIndentedLine("Severity", issue.getString("severity"));
            pdf.addIndentedLine("Count", Integer.toString(issue.getInt("count")));
            pdf.addIndentedLine("Locations", issue.getString("location"));
            pdf.addIndentedHyperlink("Root Cause/How to fix",
                apiUrl + "coding_rules?q=" + issue.getString("ruleKey") + "&open=" + issue.getString("ruleKey"),
                issue.getString("ruleKey"));
        }
    }

    private static Map<String, JSONObject> groupHotspotsByRule(JSONArray hotspots) {
        Map<String, JSONObject> result = new HashMap<>();
        for (int i = 0; i < hotspots.length(); i++) {
            JSONObject obj = hotspots.getJSONObject(i);
            String ruleKey = obj.getString("ruleKey");
            String file = extractComponent(obj.getString("component"));
            String textLine = Integer.toString(obj.getJSONObject("textRange").getInt("startLine"));
            if (result.containsKey(ruleKey)) {
                JSONObject existing = result.get(ruleKey);
                existing.put("count", existing.getInt("count") + 1);
                existing.put("location", existing.getString("location") + " | " + file + ": " + textLine);
            } else {
                JSONObject newObj = new JSONObject();
                newObj.put("ruleKey", ruleKey);
                newObj.put("count", 1);
                newObj.put("vulnerabilityProbability", obj.getString("vulnerabilityProbability"));
                newObj.put("message", obj.getString("message"));
                newObj.put("location", file + ": " + textLine);
                result.put(ruleKey, newObj);
            }
        }
        return result;
    }

    private static Map<String, JSONObject> groupIssuesByRule(JSONArray issues) {
        Map<String, JSONObject> result = new HashMap<>();
        for (int i = 0; i < issues.length(); i++) {
            JSONObject obj = issues.getJSONObject(i);
            String ruleKey = obj.getString("rule");
            String file = extractComponent(obj.getString("component"));
            if (result.containsKey(ruleKey)) {
                JSONObject existing = result.get(ruleKey);
                existing.put("count", existing.getInt("count") + 1);
                String loc = existing.getString("location");
                if (obj.has("textRange")) {
                    String textLine = Integer.toString(obj.getJSONObject("textRange").getInt("startLine"));
                    existing.put("location", loc + " | " + file + ": " + textLine);
                } else {
                    existing.put("location", loc + " | " + file);
                }
            } else {
                JSONObject newObj = new JSONObject();
                newObj.put("ruleKey", ruleKey);
                newObj.put("count", 1);
                newObj.put("severity", obj.getString("severity"));
                newObj.put("message", obj.getString("message"));
                newObj.put("type", obj.getString("type"));
                if (obj.has("textRange")) {
                    String textLine = Integer.toString(obj.getJSONObject("textRange").getInt("startLine"));
                    newObj.put("location", file + ": " + textLine);
                } else {
                    newObj.put("location", file);
                }
                result.put(ruleKey, newObj);
            }
        }
        return result;
    }

    private static String extractComponent(String component) {
        return component.contains(":") ? component.split(":", 2)[1].trim() : component;
    }

    private static String ratingToLetter(String value) {
        switch (value) {
            case "1.0": return "A";
            case "2.0": return "B";
            case "3.0": return "C";
            case "4.0": return "D";
            case "5.0": return "E";
            default: throw new AssertionError("Unknown rating: " + value);
        }
    }

    private static String minsToDaysHoursMins(int minutes) {
        int days = minutes / (24 * 60);
        int hours = (minutes % (24 * 60)) / 60;
        int mins = minutes % 60;
        return String.format("%dd %02dh %02dm", days, hours, mins);
    }
}
