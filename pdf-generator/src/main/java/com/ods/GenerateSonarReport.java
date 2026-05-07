package com.ods;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GenerateSonarReport {

    public static void main(String[] args) throws IOException {
        Map<String, String> params = parseArgs(args);

        String sonarUrl = params.get("--sonar-url");
        String token    = params.get("--token");
        String project  = params.get("--project");
        String branch   = params.get("--branch");
        String output   = params.getOrDefault("--output", "sonarqube-report.pdf");

        if (sonarUrl == null || token == null || project == null) {
            System.err.println("Usage: java -jar sonar-report-1.0-jar-with-dependencies.jar"
                + " --sonar-url <url> --token <token> --project <key> [--branch <branch>] [--output <file.pdf>]");
            System.exit(1);
        }

        new ReportBuilder(sonarUrl, token, project, branch).build(output);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].startsWith("--")) {
                params.put(args[i], args[i + 1]);
                i++;
            }
        }
        return params;
    }
}
