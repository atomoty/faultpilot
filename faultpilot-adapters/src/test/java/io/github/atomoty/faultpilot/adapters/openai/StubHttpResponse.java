package io.github.atomoty.faultpilot.adapters.openai;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/** Minimal {@link HttpResponse} for tests: only status and string body matter. */
record StubHttpResponse(int status, String body) implements HttpResponse<String> {

    @Override
    public int statusCode() {
        return status;
    }

    @Override
    public String body() {
        return body;
    }

    @Override
    public HttpRequest request() {
        return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return URI.create("https://api.openai.com/v1/chat/completions");
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
