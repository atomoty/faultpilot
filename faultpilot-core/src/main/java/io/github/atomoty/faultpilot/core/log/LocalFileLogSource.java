package io.github.atomoty.faultpilot.core.log;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/**
 * Resolved configuration for reading a project's local log files. Built by the server from the
 * project's {@code logs} block; kept Spring-free so the reader stays unit-testable.
 *
 * @param pattern optional custom line-head regex; null/blank uses {@link LogLineParser#DEFAULT_PATTERN}
 * @param zone    zone used to interpret timestamps that carry no offset (spec §7.1)
 */
public record LocalFileLogSource(
        List<String> paths,
        String pattern,
        Charset charset,
        ZoneId zone
) {
    public LocalFileLogSource {
        paths = paths == null ? List.of() : List.copyOf(paths);
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
        zone = zone == null ? ZoneId.systemDefault() : zone;
    }
}
