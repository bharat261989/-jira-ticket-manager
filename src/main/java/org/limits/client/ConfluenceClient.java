package org.limits.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.limits.config.ConfluenceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client for Confluence Server REST API (create page, etc.).
 */
public class ConfluenceClient {

    private static final Logger LOG = LoggerFactory.getLogger(ConfluenceClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConfluenceConfig config;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeader;

    public ConfluenceClient(ConfluenceConfig config) {
        this.config = config;
        this.baseUrl = config.getBaseUrl().replaceAll("/$", "");
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (config.getUsername() + ":" + config.getApiToken()).getBytes(StandardCharsets.UTF_8));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectionTimeout()))
                .build();
    }

    /**
     * Create a page in the given space with the given title and HTML body (storage format).
     *
     * @param spaceKey Confluence space key
     * @param title    Page title
     * @param bodyHtml Body content in Confluence storage format (XHTML-like, e.g. &lt;p&gt;, &lt;table&gt;, etc.)
     * @return Created page ID and web UI link
     */
    public CreatePageResult createPage(String spaceKey, String title, String bodyHtml) {
        String uri = baseUrl + "/rest/api/content";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "page");
        body.put("title", title);
        body.putObject("space").put("key", spaceKey);
        body.putObject("body")
                .putObject("storage")
                .put("value", bodyHtml)
                .put("representation", "storage");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader)
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String responseBody = response.body();

            if (status < 200 || status >= 300) {
                LOG.error("Confluence create page failed: {} {}", status, responseBody);
                throw new ConfluenceClientException("Confluence API error: " + status + " - " + responseBody);
            }

            JsonNode json = MAPPER.readTree(responseBody);
            String id = json.path("id").asText();
            String linkPrefix = json.path("_links").path("webui").asText();
            String pageUrl = baseUrl + linkPrefix;
            return new CreatePageResult(id, pageUrl);
        } catch (IOException e) {
            throw new ConfluenceClientException("Failed to create Confluence page", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConfluenceClientException("Interrupted while creating Confluence page", e);
        }
    }

    private static final int SPACE_PAGE_LIMIT = 100;

    /**
     * Retrieve all pages in a Confluence space (paginates through the space content API).
     *
     * @param spaceKey Confluence space key
     * @return List of page summaries (id, title, webUrl)
     */
    public List<PageSummary> getSpacePages(String spaceKey) {
        List<PageSummary> all = new ArrayList<>();
        int start = 0;
        int size;
        do {
            String uri = baseUrl + "/rest/api/space/" + spaceKey + "/content/page?limit=" + SPACE_PAGE_LIMIT + "&start=" + start;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Authorization", authHeader)
                    .timeout(Duration.ofMillis(config.getReadTimeout()))
                    .GET()
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new ConfluenceClientException("Failed to list Confluence space pages", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConfluenceClientException("Interrupted while listing Confluence pages", e);
            }

            int status = response.statusCode();
            String responseBody = response.body();
            if (status < 200 || status >= 300) {
                LOG.error("Confluence list space pages failed: {} {}", status, responseBody);
                throw new ConfluenceClientException("Confluence API error: " + status + " - " + responseBody);
            }

            try {
                JsonNode json = MAPPER.readTree(responseBody);
                JsonNode results = json.path("results");
                size = json.path("size").asInt(0);
                for (JsonNode node : results) {
                    String id = node.path("id").asText();
                    String title = node.path("title").asText("");
                    String webui = node.path("_links").path("webui").asText("");
                    String webUrl = webui.isEmpty() ? null : baseUrl + webui;
                    all.add(new PageSummary(id, title, webUrl));
                }
                start += size;
            } catch (IOException e) {
                throw new ConfluenceClientException("Failed to parse Confluence space response", e);
            }
        } while (size >= SPACE_PAGE_LIMIT);

        return all;
    }

    public static class PageSummary {
        private final String id;
        private final String title;
        private final String webUrl;

        public PageSummary(String id, String title, String webUrl) {
            this.id = id;
            this.title = title;
            this.webUrl = webUrl;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getWebUrl() { return webUrl; }
    }

    public static class CreatePageResult {
        private final String pageId;
        private final String pageUrl;

        public CreatePageResult(String pageId, String pageUrl) {
            this.pageId = pageId;
            this.pageUrl = pageUrl;
        }

        public String getPageId() {
            return pageId;
        }

        public String getPageUrl() {
            return pageUrl;
        }
    }

    public static class ConfluenceClientException extends RuntimeException {
        public ConfluenceClientException(String message) {
            super(message);
        }

        public ConfluenceClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
