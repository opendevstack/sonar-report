package com.ods;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class SonarApiClientTest {

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(status);
        Mockito.when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private HttpClient mockClientReturning(HttpResponse<String> response) throws IOException, InterruptedException {
        HttpClient client = Mockito.mock(HttpClient.class);
        Mockito.doReturn(response).when(client).send(any(HttpRequest.class), any());
        return client;
    }

    @Test
    void fetchDataFromURL_successfulResponse_returnsJsonObject() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(200, "{\"key\": \"value\"}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        JSONObject result = client.fetchDataFromURL("/api/test?component=", "myproject");

        assertEquals("value", result.getString("key"));
    }

    @Test
    void fetchDataFromURL_401Response_throwsIOException() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(401, "{\"errors\":[]}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "myproject"));
    }

    @Test
    void fetchDataFromURL_404Response_throwsIOException() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(404, "{\"errors\":[]}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "myproject"));
    }

    @Test
    void fetchDataFromURL_withBranch_doesNotThrow() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(200, "{\"ok\": true}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", "main", mockClient);

        JSONObject result = client.fetchDataFromURL("/api/test?component=", "proj");

        assertTrue(result.getBoolean("ok"));
    }

    @Test
    void fetchDataFromURL_withBlankBranch_treatedAsNoBranch() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(200, "{\"ok\": true}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", "  ", mockClient);

        JSONObject result = client.fetchDataFromURL("/api/test?component=", "proj");

        assertTrue(result.getBoolean("ok"));
    }

    @Test
    void fetchDataFromURL_projectKeyWithSpecialChars_encodedInUrl() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(200, "{\"ok\": true}");

        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<HttpRequest> capturedRequest =
            new java.util.concurrent.atomic.AtomicReference<>();

        Mockito.doAnswer(inv -> {
            capturedRequest.set(inv.getArgument(0));
            return response;
        }).when(mockClient).send(any(HttpRequest.class), any());

        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);
        client.fetchDataFromURL("/api/test?component=", "my project/key");

        String url = capturedRequest.get().uri().toString();
        assertFalse(url.contains(" "), "URL should not contain raw spaces");
        assertTrue(url.contains("my+project") || url.contains("my%20project"),
            "URL should contain encoded project key");
    }
}

