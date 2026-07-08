package com.eurostat.fetcher.client;

/** Thrown when the Eurostat API keeps returning 429 after all retries are exhausted. */
public class RateLimitException extends EurostatApiException {
    public RateLimitException(String message) {
        super(message);
    }
}
