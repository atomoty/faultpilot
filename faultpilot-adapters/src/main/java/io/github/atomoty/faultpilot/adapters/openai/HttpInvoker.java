package io.github.atomoty.faultpilot.adapters.openai;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * A thin seam over the HTTP send call so the OpenAI adapter can be unit-tested with a stub that
 * returns canned responses or throws, without making real network calls.
 */
public interface HttpInvoker {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
