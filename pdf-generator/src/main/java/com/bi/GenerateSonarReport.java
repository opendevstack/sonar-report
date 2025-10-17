import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



public class GenerateSonarReport {

    private static HttpClient createUnsafeHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());

            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm(null);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .build();

        } catch (java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
            throw new IllegalStateException("The insecure HttpClient couldn't be created", e);
        }
    }
    
    public static JSONObject fetchDataFromURL(String url, String call, String token, String projectKey) throws IOException, InterruptedException {
        HttpClient client = createUnsafeHttpClient();
        String encodedProjectKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8);
        String fullURL = String.format("%s%s%s", url, call, encodedProjectKey);
        

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullURL))
                .header("Authorization", "Bearer " + token)
                .build();
        
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return json;
        } else {
            throw new IOException("Error at obtaining data from the URL: Status Code " + response.statusCode() + ", Body: " + response.body());
        }
    }

    @SuppressWarnings("empty-statement")
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java -jar target/pdf-generator-1.0-SNAPSHOT-jar-with-dependencies.jar <apiUrl> <authToken> <project>");
            System.exit(1);
        }
        String apiUrl = args[0]; 
        String authToken = args[1]; 
        String project = args[2]; 

        // We create the initial pdf
        PDFReportWriter pdf = new PDFReportWriter();
        JSONObject data = null;
        JSONArray dataArray = null;
        try {
            data = fetchDataFromURL(apiUrl, "/api/navigation/component?component=", authToken, project);
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        String name = data.getString("name");

        // Introduction
        pdf.tittle2Font();
        pdf.addLine("INTRODUCTION");
        pdf.bodyFont();
        pdf.addLine("• This document contains results of the code analysis of " + name + ".");

        String date = "• Date: " + data.getString("analysisDate");
        String branch = "• Branch: " + data.getString("branch");
        pdf.addLine(branch);
        pdf.addLine(date.replace("T", " "));

        // Configuration
        pdf.tittle2Font();
        pdf.addLine("CONFIGURATION");

        String qualityProfiles = "• Quality Profiles: ";
        JSONArray qualityProfilesList = data.getJSONArray("qualityProfiles");

        for (int i = 0; i < qualityProfilesList.length(); i++) {
            JSONObject qualityProfile = qualityProfilesList.getJSONObject(i);
            if (i+1<qualityProfilesList.length()){
                String aux = qualityProfile.getString("name") + " [" + qualityProfile.getString("language") + "], ";
                qualityProfiles += aux;
            } else {
                String aux = qualityProfile.getString("name") + " [" + qualityProfile.getString("language") + "].";
                qualityProfiles += aux;
            }
        }

        pdf.bodyFont();
        pdf.addLine(qualityProfiles);

        String qualityGate ="• Quality Gate: ";
        JSONObject qualityGateList = data.getJSONObject("qualityGate");
        qualityGate += qualityGateList.getString("name") + ".";
        pdf.addLine(qualityGate);

        // SYNTHESYS
        pdf.tittle2Font();
        pdf.addLine("SYNTHESIS");

        // ANALYSIS STATUS
        pdf.tittle3Font();
        pdf.addLine("ANALYSIS STATUS");


        String[] headers = { "Reliability", "Security", "Security Review", "Maintainability" };
        List<String[]> rows = new ArrayList<>();

        data = null;
        try {
            
            data = fetchDataFromURL(apiUrl, "/api/measures/component?metricKeys=reliability_rating,software_quality_maintainability_rating,security_rating,security_review_rating&component=", authToken, project);
            data = data.getJSONObject("component");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray measuresList = data.getJSONArray("measures");
        String[] measures = new String[4];
        for (int i = 0; i < measuresList.length(); i++) {
            JSONObject measure = measuresList.getJSONObject(i);
            String aux = "";
            switch (measure.getString("value")) {
                case "1.0": aux = "A"; break;
                case "2.0": aux = "B"; break;
                case "3.0": aux = "C"; break;
                case "4.0": aux = "D"; break;
                case "5.0": aux = "E"; break;
                default:
                    throw new AssertionError();
            }
            measures[i] = aux;
        }


        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // QUALITY GATE STATUS
        pdf.tittle3Font();
        pdf.addLine("QUALITY GATE STATUS");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/qualitygates/project_status?projectKey=", authToken, project);
            data = data.getJSONObject("projectStatus");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        pdf.bodyFont();
        pdf.addLine("| Quality Gate Status | " + data.getString("status") + " |");


        // METRICS
        pdf.tittle3Font();
        pdf.addLine("METRICS");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/measures/component?metricKeys=duplicated_lines_density,comment_lines_density,ncloc,complexity,cognitive_complexity,coverage&component=", authToken, project);
            data = data.getJSONObject("component");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }
        
        headers = new String[] { "Coverage", "Duplications", "Comment Density", "Lines of Code", "Cyclomatic Complexity", "Cognitive Complexity" };
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
                if (metric.contains("density") || metric.equals("coverage")) {
                    measures[index] = value + "%";
                } else {
                    measures[index] = value;
                }
            }
        }
        int totalLinesOfCode = Integer.parseInt(measures[3]);
        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        
        // TESTS
        pdf.tittle3Font();
        pdf.addLine("TESTS");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/measures/component?metricKeys=duplicated_lines_density,comment_lines_density,ncloc,complexity,cognitive_complexity,coverage&component=", authToken, project);
            data = data.getJSONObject("component");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }
        
        headers = new String[] { "Total", "Success Rate", "Skipped", "Errors", "Failures" };
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
                if (metric.equals("test_success_density")) {
                    measures[index] = value + "%";
                } else {
                    measures[index] = value;
                }
            }
        }
        
        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // DETAILED TECHNICAL DEBTS
        pdf.tittle3Font();
        pdf.addLine("DETAILED TECHNICAL DEBTS");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/measures/component?metricKeys=reliability_remediation_effort,security_remediation_effort,sqale_index&component=", authToken, project);
            data = data.getJSONObject("component");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }
        
        headers = new String[] { "Reliability", "Security", "Maintainability", "Total" };
        rows = new ArrayList<>();

        metricIndex = new HashMap<>();
        metricIndex.put("reliability_remediation_effort", 0);
        metricIndex.put("security_remediation_effort", 1);
        metricIndex.put("sqale_index", 2);

        measuresList = data.getJSONArray("measures");
        measures = new String[4];
        Arrays.fill(measures, "0d 0h 0m");
        int totalmins = 0;
        for (int i = 0; i < measuresList.length(); i++) {
            JSONObject measure = measuresList.getJSONObject(i);
            String metric = measure.getString("metric");
            String value = measure.getString("value");
            if (metricIndex.containsKey(metric)) {
                int index = metricIndex.get(metric);
                int minutes = Integer.parseInt(value);
                totalmins += minutes;

                measures[index] = minsToDaysHoursMins(minutes);
            }
        }
        measures[3] = minsToDaysHoursMins(totalmins);

        rows.add(measures);
        pdf.drawTable(500, headers, rows);

        // LINES PER LANGUAGE
        pdf.tittle3Font();
        pdf.addLine("LINES PER LANGUAGE");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/measures/component?metricKeys=ncloc_language_distribution&component=", authToken, project);
            data = data.getJSONObject("component");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }
        
        headers = new String[] { "Language", "Number of Lines", "Total Percent" };
        rows = new ArrayList<>();

        measuresList = data.getJSONArray("measures");

        JSONObject measure = measuresList.getJSONObject(0);

        String rawLanguages = measure.getString("value");

        for (String pair : rawLanguages.split(";")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                int lines = Integer.parseInt(parts[1]);
                String percent = String.format("%.2f%%", (lines * 100.0) / totalLinesOfCode);
                rows.add(new String[] { parts[0], parts[1], percent});
            }
        }

        pdf.drawTable(500, headers, rows);

        // SECURITY HOTSPOTS

        pdf.tittle2Font();
        pdf.addLine("SECURITY HOTSPOTS");
        pdf.tittle3Font();
        pdf.addLine("SECURITY HOTSPOTS COUNT BY CATEGORY AND PRIORITY");

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/security_reports/show?standard=sonarsourceSecurity&project=", authToken, project);
            dataArray = data.getJSONArray("categories");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }
        
        headers = new String[] { "Categories", "Security", "Security Hotspots" };
        rows = new ArrayList<>();
        Map<String,String> categories = new HashMap<>();
        categories.put("buffer-overflow", "Buffer Overflow");
        categories.put("sql-injection", "SQL Injection");
        categories.put("rce", "Code Injection (RCE)");
        categories.put("object-injection", "Object Injection");
        categories.put("command-injection", "Command Injection");
        categories.put("path-traversal-injection", "Path Traversal Injection");
        categories.put("ldap-injection", "LDAP Injection");
        categories.put("xpath-injection", "XPath Injection");
        categories.put("log-injection", "Log Injection");
        categories.put("xxe", "XML External Entity(XXE)");
        categories.put("xss", "Cross-Site Scripting (XSS)");
        categories.put("dos", "Denial of Service (DoS)");
        categories.put("ssrf", "Server-Side Request Forgery (SSRF)");
        categories.put("csrf", "Cross-Site Request Forgery (CSRF)");
        categories.put("http-response-splitting", "HTTP Responde Splitting");
        categories.put("open-redirect", "Open Redirect");
        categories.put("weak-cryptography", "Weak Cryptography");
        categories.put("auth", "Authentication");
        categories.put("insecure-conf", "Insecure Configuration");
        categories.put("file-manipulation", "File Manipulation");
        categories.put("encrypt-data", "Encryption of Sensitive Data");
        categories.put("traceability", "Traceability");
        categories.put("permission", "Permission");
        categories.put("others", "Others");

        Map<Integer,String> rating = new HashMap<>();
        rating.put(1,"[A]");
        rating.put(2,"[B]");
        rating.put(3,"[C]");
        rating.put(4,"[D]");
        rating.put(5,"[E]");

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject object = dataArray.getJSONObject(i);
            String category = object.getString("category");
            Integer vuls = object.getInt("vulnerabilities");
            Integer vulsRate = 1;
            if(object.has("vulnerabilityRating")) vulsRate = object.getInt("vulnerabilityRating");
            Integer hotSpots = object.getInt("toReviewSecurityHotspots");
            Integer hotSpotsRate = object.getInt("securityReviewRating");
            String vulsText = String.valueOf(vuls) + "  " + rating.get(vulsRate);
            String hotSpotsText = String.valueOf(hotSpots) + "  " + rating.get(hotSpotsRate);

            rows.add(new String[] { categories.get(category), vulsText, hotSpotsText});
        }

        pdf.drawTable(500, headers, rows);

        // SECURITY HOTSPOTS LIST

        pdf.tittle3Font();
        pdf.addLine("SECURITY HOTSPOT LIST");


        try {
            int pageIndex = 1;
            int total = Integer.MAX_VALUE;
            dataArray = new JSONArray();
            while ((pageIndex - 1) * 500 < total) {
                data = fetchDataFromURL(
                    apiUrl,
                    String.format("/api/hotspots/search?status=TO_REVIEW&ps=500&pageIndex=%d&project=", pageIndex),
                    authToken,
                    project
                );

                JSONArray currentPageHotspots = data.getJSONArray("hotspots");
                for (int i = 0; i < currentPageHotspots.length(); i++) {
                    dataArray.put(currentPageHotspots.getJSONObject(i));
                }

                if (data.has("paging")) {
                    total = data.getJSONObject("paging").getInt("total");
                }

                pageIndex++;
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        Map<String, JSONObject> hotspotMap = new HashMap<>();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject originalObj = dataArray.getJSONObject(i);
            String ruleKey = originalObj.getString("ruleKey");

            if (hotspotMap.containsKey(ruleKey)) {
                // It exists
                JSONObject existing = hotspotMap.get(ruleKey);
                int currentCount = existing.getInt("count");
                existing.put("count", currentCount + 1);
                String currentLocations = existing.getString("location");
                String file = originalObj.getString("component");
                file = file.contains(":") ? file.split(":", 2)[1].trim() : file;
                JSONObject textLines = originalObj.getJSONObject("textRange");
                String textLine = Integer.toString(textLines.getInt("startLine"));
                existing.put("location", currentLocations + " | " + file + ": " + textLine);

            } else {
                // Is new
                JSONObject newObj = new JSONObject();
                JSONObject textLines = originalObj.getJSONObject("textRange");
                String file = originalObj.getString("component");
                file = file.contains(":") ? file.split(":", 2)[1].trim() : file;
                String textLine = Integer.toString(textLines.getInt("startLine"));
                newObj.put("ruleKey", ruleKey);
                newObj.put("count", 1);
                newObj.put("vulnerabilityProbability", originalObj.getString("vulnerabilityProbability"));
                newObj.put("message", originalObj.getString("message"));
                newObj.put("location", file + ": " + textLine);
                

                hotspotMap.put(ruleKey, newObj);
            }
        }
        // Convert map to JSONArray
        JSONArray hotspotArray = new JSONArray(hotspotMap.values());
        for (int i = 0; i < hotspotArray.length(); i++) {
            JSONObject hotspotObject = hotspotArray.getJSONObject(i);
            pdf.startBulletEntry(hotspotObject.getString("message"));
            pdf.addIndentedLine("Vulnerability Probability", hotspotObject.getString("vulnerabilityProbability"));
            pdf.addIndentedLine("Count", Integer.toString(hotspotObject.getInt("count")));
            pdf.addIndentedLine("Locations", hotspotObject.getString("location"));
            pdf.addIndentedHyperlink("Root Cause/How to fix", apiUrl+"coding_rules?q="+hotspotObject.getString("ruleKey")+"&open="+hotspotObject.getString("ruleKey"),hotspotObject.getString("ruleKey"));
        }

        // -----------------------------

        // ISSUES
        pdf.tittle2Font();
        pdf.addLine("ISSUES");
        pdf.tittle3Font();
        pdf.addLine("ISSUES COUNT BY SEVERITY AND TYPES");

        headers = new String[] { "Type / Severity", "INFO", "MINOR", "MAJOR", "CRITICAL", "BLOCKER" };
        rows = new ArrayList<>();

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/issues/search?types=BUG&facets=severities&componentKeys=", authToken, project);
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray bugArray = data.getJSONArray("facets");
        bugArray = bugArray.getJSONObject(0).getJSONArray("values");
        rows.add(new String[]{ "Bug", String.valueOf(bugArray.getJSONObject(4).getInt("count")), String.valueOf(bugArray.getJSONObject(0).getInt("count")), String.valueOf(bugArray.getJSONObject(1).getInt("count")), String.valueOf(bugArray.getJSONObject(2).getInt("count")), String.valueOf(bugArray.getJSONObject(3).getInt("count")) });

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/issues/search?types=VULNERABILITY&facets=severities&componentKeys=", authToken, project);
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray vulArray = data.getJSONArray("facets");
        vulArray = vulArray.getJSONObject(0).getJSONArray("values");
        rows.add(new String[]{ "Vulnerability", String.valueOf(vulArray.getJSONObject(4).getInt("count")), String.valueOf(vulArray.getJSONObject(0).getInt("count")), String.valueOf(vulArray.getJSONObject(1).getInt("count")), String.valueOf(vulArray.getJSONObject(2).getInt("count")), String.valueOf(vulArray.getJSONObject(3).getInt("count")) });

        try {
            
            data = fetchDataFromURL(apiUrl, "/api/issues/search?types=CODE_SMELL&facets=severities&componentKeys=", authToken, project);
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        JSONArray codeSmellArray = data.getJSONArray("facets");
        codeSmellArray = codeSmellArray.getJSONObject(0).getJSONArray("values");
        rows.add(new String[]{ "Code Smell", String.valueOf(codeSmellArray.getJSONObject(4).getInt("count")), String.valueOf(codeSmellArray.getJSONObject(0).getInt("count")), String.valueOf(codeSmellArray.getJSONObject(1).getInt("count")), String.valueOf(codeSmellArray.getJSONObject(2).getInt("count")), String.valueOf(codeSmellArray.getJSONObject(3).getInt("count")) });
        
        pdf.drawTable(500, headers, rows);

        // ISSUES LIST
        pdf.tittle3Font();
        pdf.addLine("ISSUES LIST");

        try {
            int pageIndex = 1;
            int total = Integer.MAX_VALUE;
            dataArray = new JSONArray();
            while ((pageIndex - 1) * 500 < total) {
                data = fetchDataFromURL(
                    apiUrl,
                    String.format("/api/issues/search?issueStatuses=OPEN&ps=500&pageIndex=%d&componentKeys=", pageIndex),
                    authToken,
                    project
                );

                JSONArray currentPageHotspots = data.getJSONArray("issues");
                for (int i = 0; i < currentPageHotspots.length(); i++) {
                    dataArray.put(currentPageHotspots.getJSONObject(i));
                }

                if (data.has("paging")) {
                    total = data.getJSONObject("paging").getInt("total");
                }

                pageIndex++;
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error at doing the HTTP petition: " + e.getMessage());
        }

        Map<String, JSONObject> issuesMap = new HashMap<>();

        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject originalObj = dataArray.getJSONObject(i);
            String ruleKey = originalObj.getString("rule");

            if (issuesMap.containsKey(ruleKey)) {
                // It exists
                JSONObject existing = issuesMap.get(ruleKey);
                int currentCount = existing.getInt("count");
                existing.put("count", currentCount + 1);
                String currentLocations = existing.getString("location");
                String file = originalObj.getString("component");
                file = file.contains(":") ? file.split(":", 2)[1].trim() : file;
                JSONObject textLines = originalObj.getJSONObject("textRange");
                String textLine = Integer.toString(textLines.getInt("startLine"));
                existing.put("location", currentLocations + " | " + file + ": " + textLine);

            } else {
                // Is new
                JSONObject newObj = new JSONObject();
                JSONObject textLines = originalObj.getJSONObject("textRange");
                String file = originalObj.getString("component");
                file = file.contains(":") ? file.split(":", 2)[1].trim() : file;
                String textLine = Integer.toString(textLines.getInt("startLine"));
                newObj.put("ruleKey", ruleKey);
                newObj.put("count", 1);
                newObj.put("severity", originalObj.getString("severity"));
                newObj.put("message", originalObj.getString("message"));
                newObj.put("type", originalObj.getString("type"));
                newObj.put("location", file + ": " + textLine);

                issuesMap.put(ruleKey, newObj);
            }
        }

        // Map to JsonArray
        JSONArray issuesArray = new JSONArray(issuesMap.values());

        for (int i = 0; i < issuesArray.length(); i++) {
            JSONObject issuesObject = issuesArray.getJSONObject(i);
            pdf.startBulletEntry(issuesObject.getString("message"));
            pdf.addIndentedLine("Type", issuesObject.getString("type"));
            pdf.addIndentedLine("Severity", issuesObject.getString("severity"));
            pdf.addIndentedLine("Count", Integer.toString(issuesObject.getInt("count")));
            pdf.addIndentedLine("Locations", issuesObject.getString("location"));
            pdf.addIndentedHyperlink("Root Cause/How to fix", apiUrl+"coding_rules?q="+issuesObject.getString("ruleKey")+"&open="+issuesObject.getString("ruleKey"),issuesObject.getString("ruleKey"));
        }
        pdf.insertIndexAtBeginning();
        pdf.addCoverPage("SonarQube Report", "Generated for "+ project);
        pdf.save("sonarqube-report.pdf");
    }

    private static String minsToDaysHoursMins(int minutes) {
        int days = minutes / (24 * 60);
        int hours = (minutes % (24 * 60)) / 60;
        int mins = minutes % 60;

        return String.format("%dd %02dh %02dm", days, hours, mins);
    }

    private String getCountAsString(JSONArray jsonArray, int index) {
    if (jsonArray != null && index >= 0 && index < jsonArray.length()) {
        try {
            return String.valueOf(jsonArray.getJSONObject(index).getString("count"));
        } catch (JSONException e) {
            System.err.println("Error at obtaning 'count' in the index " + index + ": " + e.getMessage());
            return ""; 
        }
    }
    return "";
}

}
