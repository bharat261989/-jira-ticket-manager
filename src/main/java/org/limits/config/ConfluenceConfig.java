package org.limits.config;

/**
 * Confluence Server connection and default space configuration.
 */
public class ConfluenceConfig {

    private String baseUrl;
    private String username;
    private String apiToken;
    private String defaultSpaceKey;
    private int connectionTimeout = 5000;
    private int readTimeout = 30000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getDefaultSpaceKey() {
        return defaultSpaceKey;
    }

    public void setDefaultSpaceKey(String defaultSpaceKey) {
        this.defaultSpaceKey = defaultSpaceKey;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}
