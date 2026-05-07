package com.ods;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;

public class SonarApiClient {

    private final String apiUrl;
    private final String authToken;
    private final HttpClient httpClient;

    public SonarApiClient(String apiUrl, String authToken) {
        this.apiUrl = apiUrl;
        this.authToken = authToken;
        this.httpClient = createUnsafeHttpClient();
    }

    // Creates an HttpClient that accepts all SSL certificates and disables hostname verification.
    private HttpClient createUnsafeHttpClient() {
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

    public JSONObject fetchDataFromURL(String call, String projectKey) throws IOException, InterruptedException {
        String encodedProjectKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8);
        String fullURL = String.format("%s%s%s", apiUrl, call, encodedProjectKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullURL))
                .header("Authorization", "Bearer " + authToken)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return json;
        } else {
            throw new IOException("Error at obtaining data from the URL: Status Code " + response.statusCode() + ", Body: " + response.body());
        }
    }
}
