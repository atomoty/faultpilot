package io.github.atomoty.faultpilot.adapters.openai;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;

/** Test helper to read an {@link HttpRequest}'s body publisher back into a String. */
final class BodyPublishers {

    private BodyPublishers() {
    }

    static String stringOf(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        StringBuilder sb = new StringBuilder();
        LinkedBlockingQueue<ByteBuffer> queue = new LinkedBlockingQueue<>();
        boolean[] done = {false};
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                queue.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done[0] = true;
            }

            @Override
            public void onComplete() {
                done[0] = true;
            }
        });
        // Synchronous publishers (ofString) deliver immediately; drain the queue.
        ByteBuffer buf;
        while ((buf = queue.poll()) != null) {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
