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

    // --- successful responses ---

    @Test
    void fetchDataFromURL_successfulResponse_returnsJsonObject() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(200, "{\"key\": \"value\"}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        JSONObject result = client.fetchDataFromURL("/api/test?component=", "myproject");

        assertEquals("value", result.getString("key"));
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

    // --- error status codes: message content ---

    @Test
    void fetchDataFromURL_404WithSonarErrorMsg_messageIncludesDetail() throws Exception {
        String body = "{\"errors\":[{\"msg\":\"Component 'potato' on branch 'main' not found\"}]}";
        HttpClient mockClient = mockClientReturning(mockResponse(404, body));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/navigation/component?component=", "potato"));

        assertTrue(ex.getMessage().contains("HTTP 404"), "message should include status code");
        assertTrue(ex.getMessage().contains("Component 'potato' on branch 'main' not found"),
            "message should include SonarQube error detail");
    }

    @Test
    void fetchDataFromURL_401WithSonarErrorMsg_messageIncludesDetail() throws Exception {
        String body = "{\"errors\":[{\"msg\":\"Authentication required\"}]}";
        HttpClient mockClient = mockClientReturning(mockResponse(401, body));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "myproject"));

        assertTrue(ex.getMessage().contains("HTTP 401"));
        assertTrue(ex.getMessage().contains("Authentication required"));
    }

    @Test
    void fetchDataFromURL_multipleErrors_allConcatenatedInMessage() throws Exception {
        String body = "{\"errors\":[{\"msg\":\"First error\"},{\"msg\":\"Second error\"}]}";
        HttpClient mockClient = mockClientReturning(mockResponse(400, body));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "proj"));

        assertTrue(ex.getMessage().contains("First error"));
        assertTrue(ex.getMessage().contains("Second error"));
    }

    @Test
    void fetchDataFromURL_nonJsonErrorBody_rawBodyIncludedInMessage() throws Exception {
        String body = "Service Unavailable";
        HttpClient mockClient = mockClientReturning(mockResponse(503, body));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "proj"));

        assertTrue(ex.getMessage().contains("HTTP 503"));
        assertTrue(ex.getMessage().contains("Service Unavailable"));
    }

    @Test
    void fetchDataFromURL_emptyErrorsArray_rawBodyIncludedInMessage() throws Exception {
        String body = "{\"errors\":[]}";
        HttpClient mockClient = mockClientReturning(mockResponse(404, body));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "myproject"));

        assertTrue(ex.getMessage().contains("HTTP 404"));
        assertTrue(ex.getMessage().contains(body));
    }

    @Test
    void fetchDataFromURL_500Response_throwsIOException() throws Exception {
        HttpClient mockClient = mockClientReturning(mockResponse(500, "{\"errors\":[{\"msg\":\"Internal error\"}]}"));
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "myproject"));

        assertTrue(ex.getMessage().contains("HTTP 500"));
        assertTrue(ex.getMessage().contains("Internal error"));
    }

    // --- InterruptedException handling ---

    @Test
    @SuppressWarnings("unchecked")
    void fetchDataFromURL_interruptedException_wrappedAsIOException() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        Mockito.doThrow(new InterruptedException("connection interrupted"))
            .when(mockClient).send(any(HttpRequest.class), any());
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        Thread.currentThread().interrupted(); // clear any prior flag
        IOException ex = assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "proj"));

        assertTrue(ex.getMessage().contains("interrupted"));
        assertInstanceOf(InterruptedException.class, ex.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchDataFromURL_interruptedException_restoresThreadInterruptFlag() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        Mockito.doThrow(new InterruptedException())
            .when(mockClient).send(any(HttpRequest.class), any());
        SonarApiClient client = new SonarApiClient("http://sonar", "token", null, mockClient);

        Thread.currentThread().interrupted(); // clear any prior flag
        assertThrows(IOException.class, () ->
            client.fetchDataFromURL("/api/test?component=", "proj"));

        assertTrue(Thread.currentThread().isInterrupted(), "thread interrupt flag should be restored");
        Thread.currentThread().interrupted(); // clean up
    }

    // --- URL encoding ---

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

    @Test
    void fetchDataFromURL_withBranch_includesInUrl() throws Exception {
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(200, "{\"ok\": true}");

        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<HttpRequest> capturedRequest =
            new java.util.concurrent.atomic.AtomicReference<>();

        Mockito.doAnswer(inv -> {
            capturedRequest.set(inv.getArgument(0));
            return response;
        }).when(mockClient).send(any(HttpRequest.class), any());

        SonarApiClient client = new SonarApiClient("http://sonar", "token", "feature/test", mockClient);
        client.fetchDataFromURL("/api/test?component=", "myproject");

        String url = capturedRequest.get().uri().toString();
        assertTrue(url.contains("&branch="), "URL should contain branch parameter");
        assertTrue(url.contains("feature%2Ftest"), "branch value should be URL-encoded");
    }

    @Test
    void fetchDataFromURL_withoutBranch_noBranchInUrl() throws Exception {
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
        client.fetchDataFromURL("/api/test?component=", "myproject");

        String url = capturedRequest.get().uri().toString();
        assertFalse(url.contains("&branch="), "URL should not contain branch parameter when branch is null");
    }
}
