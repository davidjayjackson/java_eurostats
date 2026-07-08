package com.eurostat.fetcher.client;

import com.eurostat.fetcher.json.Json;
import com.eurostat.fetcher.json.JsonParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Thin client for the Eurostat dissemination API (JSON-stat 2.0), with retry
 * and exponential backoff for transient failures (429 / 5xx).
 *
 * Uses {@link HttpURLConnection} rather than {@code java.net.http.HttpClient} because this
 * runs as a LibreOffice UNO component under Java 8, where the newer HTTP client doesn't exist.
 * Retries are deliberately modest (default 3 attempts, 4s backoff cap) since a Calc formula
 * calling this blocks the UI thread while it runs.
 */
public class EurostatClient {

    private static final String DEFAULT_BASE_URL =
            "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data";
    private static final long INITIAL_BACKOFF_MILLIS = 500L;
    private static final long MAX_BACKOFF_MILLIS = 4_000L;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;

    private final String baseUrl;
    private final int maxRetries;

    public EurostatClient() {
        this(DEFAULT_BASE_URL, 3);
    }

    public EurostatClient(String baseUrl, int maxRetries) {
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
    }

    public Object fetchDataset(String datasetCode, Map<String, String> filters) throws EurostatApiException {
        URL url = buildUrl(datasetCode, filters);

        int attempt = 0;
        while (true) {
            HttpURLConnection connection;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setInstanceFollowRedirects(true);
            } catch (IOException e) {
                throw new EurostatApiException("Failed to open connection to '" + url + "'", e);
            }

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                connection.disconnect();
                if (attempt >= maxRetries) {
                    throw new EurostatApiException("Network error fetching dataset '" + datasetCode + "'", e);
                }
                backoff(attempt);
                attempt++;
                continue;
            }

            try {
                if (status == 200) {
                    String body = readBody(connection.getInputStream());
                    try {
                        return Json.parse(body);
                    } catch (JsonParseException e) {
                        throw new EurostatApiException(
                                "Failed to parse JSON response for dataset '" + datasetCode + "'", e);
                    }
                }

                if (status == 404) {
                    throw new DatasetNotFoundException(datasetCode);
                }

                if (status == 429 || status >= 500) {
                    if (attempt >= maxRetries) {
                        if (status == 429) {
                            throw new RateLimitException("Rate limited (429) after " + (maxRetries + 1)
                                    + " attempts for dataset '" + datasetCode + "'");
                        }
                        throw new EurostatApiException("Server error (" + status + ") after " + (maxRetries + 1)
                                + " attempts for dataset '" + datasetCode + "'");
                    }
                    String retryAfter = connection.getHeaderField("Retry-After");
                    backoffFor(attempt, retryAfter);
                    attempt++;
                    continue;
                }

                throw new EurostatApiException("Unexpected HTTP status " + status + " for dataset '" + datasetCode + "'");
            } catch (IOException e) {
                throw new EurostatApiException("Failed to read response body for dataset '" + datasetCode + "'", e);
            } finally {
                connection.disconnect();
            }
        }
    }

    private String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private void backoffFor(int attempt, String retryAfterHeader) throws EurostatApiException {
        long delayMillis;
        if (retryAfterHeader != null) {
            try {
                delayMillis = Long.parseLong(retryAfterHeader.trim()) * 1000L;
            } catch (NumberFormatException e) {
                delayMillis = exponentialDelay(attempt);
            }
        } else {
            delayMillis = exponentialDelay(attempt);
        }
        sleep(delayMillis);
    }

    private void backoff(int attempt) throws EurostatApiException {
        sleep(exponentialDelay(attempt));
    }

    private void sleep(long delayMillis) throws EurostatApiException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EurostatApiException("Interrupted during retry backoff", e);
        }
    }

    private long exponentialDelay(int attempt) {
        long delay = INITIAL_BACKOFF_MILLIS * (1L << attempt);
        long jitter = (long) (Math.random() * 250);
        return Math.min(delay + jitter, MAX_BACKOFF_MILLIS);
    }

    private URL buildUrl(String datasetCode, Map<String, String> filters) throws EurostatApiException {
        StringBuilder sb = new StringBuilder(baseUrl).append('/').append(encode(datasetCode));
        sb.append("?format=JSON");
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            sb.append('&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        try {
            return new URL(sb.toString());
        } catch (MalformedURLException e) {
            throw new EurostatApiException("Invalid URL for dataset '" + datasetCode + "'", e);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported", e);
        }
    }
}
