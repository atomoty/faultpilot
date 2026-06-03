package io.github.atomoty.faultpilot.adapters.openai;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Production {@link HttpInvoker} backed by the JDK {@link HttpClient}. The connect timeout is set
 * here; the per-request read timeout is set on each {@link HttpRequest}.
 */
public final class JdkHttpInvoker implements HttpInvoker {

    private final HttpClient client;

    public JdkHttpInvoker(Duration connectTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
