package io.github.atomoty.faultpilot.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code faultpilot.*} configuration. See specification.md §5, design.md §10/§21.
 */
@ConfigurationProperties(prefix = "faultpilot")
public class FaultPilotProperties {

    /** Run mode, e.g. {@code mock}. */
    private String mode = "mock";

    private Ai ai = new Ai();

    private List<Project> projects = List.of();

    private Ingestion ingestion = new Ingestion();

    public Ingestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(Ingestion ingestion) {
        this.ingestion = ingestion;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    /**
     * AI provider config. {@code mock} (default), {@code openai-api} (API key, design §20.4), or the
     * experimental local {@code codex-cli} (reuses an already-authenticated Codex CLI, design §20.5;
     * local-only, never for online deployment).
     */
    public static class Ai {
        private String provider = "mock";

        // openai-api
        private String baseUrl = "https://api.openai.com";
        private String apiKey;
        private String model;
        private java.time.Duration timeout = java.time.Duration.ofSeconds(35);

        // codex-cli (experimental, local-only)
        private String codexCommand = "codex";
        private String codexModel;
        private java.time.Duration codexTimeout = java.time.Duration.ofSeconds(120);

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public java.time.Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(java.time.Duration timeout) {
            this.timeout = timeout;
        }

        public String getCodexCommand() {
            return codexCommand;
        }

        public void setCodexCommand(String codexCommand) {
            this.codexCommand = codexCommand;
        }

        public String getCodexModel() {
            return codexModel;
        }

        public void setCodexModel(String codexModel) {
            this.codexModel = codexModel;
        }

        public java.time.Duration getCodexTimeout() {
            return codexTimeout;
        }

        public void setCodexTimeout(java.time.Duration codexTimeout) {
            this.codexTimeout = codexTimeout;
        }
    }

    /**
     * Ingestion auth config (review #1). Each token is bound to a project and a set of allowed
     * environments. Full SSOT §13 controls (rate limit, idempotency, replay window) arrive with the
     * {@code events:batch} protocol in a later round.
     */
    public static class Ingestion {
        /**
         * When true (the default), event writes require a valid Bearer token bound to the project
         * and environment. Local mock demos may set this to false explicitly to allow anonymous
         * writes; doing so logs a prominent startup warning.
         */
        private boolean requireToken = true;
        private List<IngestionToken> tokens = List.of();

        public boolean isRequireToken() {
            return requireToken;
        }

        public void setRequireToken(boolean requireToken) {
            this.requireToken = requireToken;
        }

        public List<IngestionToken> getTokens() {
            return tokens;
        }

        public void setTokens(List<IngestionToken> tokens) {
            this.tokens = tokens;
        }
    }

    /** A single ingestion token bound to a project and its allowed environments. */
    public static class IngestionToken {
        private String token;
        private String projectId;
        private List<String> environments = List.of();

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public List<String> getEnvironments() {
            return environments;
        }

        public void setEnvironments(List<String> environments) {
            this.environments = environments;
        }
    }

    /**
     * A project's log source. {@code type=mock} uses the built-in demo fixtures; {@code type=local-file}
     * reads local log files at {@code paths}; {@code type=jdbc} reads a read-only DB view exposing the
     * canonical log columns (design §4.4/§10).
     */
    public static class LogsConfig {
        private String type = "mock";
        private List<String> paths = List.of();
        private String pattern;
        private String charset = "UTF-8";
        /** Zone used to interpret log timestamps without an offset (spec §7.1). Null = system default. */
        private String zone;

        // type=jdbc
        private String url;
        private String username;
        private String password;
        private String view;
        private int connectTimeoutMs = 2000;
        private int queryTimeoutMs = 3000;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getView() {
            return view;
        }

        public void setView(String view) {
            this.view = view;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getQueryTimeoutMs() {
            return queryTimeoutMs;
        }

        public void setQueryTimeoutMs(int queryTimeoutMs) {
            this.queryTimeoutMs = queryTimeoutMs;
        }
    }

    /**
     * Read-only database analysis source (design §9, §10.数据库). {@code type=mysql|postgres} enables
     * connection/long-tx/lock-wait snapshots and slow-SQL summaries; unset disables it.
     */
    public static class DatabaseConfig {
        private String type;            // mysql | postgres | null(=disabled)
        private String url;
        private String username;
        private String password;
        private String longTxThreshold = "30s";
        private int connectTimeoutMs = 2000;
        private int queryTimeoutMs = 3000;

        public boolean isEnabled() {
            return type != null && !type.isBlank();
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getLongTxThreshold() {
            return longTxThreshold;
        }

        public void setLongTxThreshold(String longTxThreshold) {
            this.longTxThreshold = longTxThreshold;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getQueryTimeoutMs() {
            return queryTimeoutMs;
        }

        public void setQueryTimeoutMs(int queryTimeoutMs) {
            this.queryTimeoutMs = queryTimeoutMs;
        }
    }

    /** A registered project. See design.md §3.1, §10. */
    public static class Project {
        private String id;
        private String displayName;
        private String integrationLevel;
        private List<String> environments = List.of();
        private int maxQueryHours = 24;
        private int maxResults = 500;
        private LogsConfig logs = new LogsConfig();
        private DatabaseConfig database = new DatabaseConfig();

        public LogsConfig getLogs() {
            return logs;
        }

        public void setLogs(LogsConfig logs) {
            this.logs = logs;
        }

        public DatabaseConfig getDatabase() {
            return database;
        }

        public void setDatabase(DatabaseConfig database) {
            this.database = database;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getIntegrationLevel() {
            return integrationLevel;
        }

        public void setIntegrationLevel(String integrationLevel) {
            this.integrationLevel = integrationLevel;
        }

        public List<String> getEnvironments() {
            return environments;
        }

        public void setEnvironments(List<String> environments) {
            this.environments = environments;
        }

        public int getMaxQueryHours() {
            return maxQueryHours;
        }

        public void setMaxQueryHours(int maxQueryHours) {
            this.maxQueryHours = maxQueryHours;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }
}
