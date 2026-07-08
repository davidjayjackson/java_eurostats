package com.eurostat.fetcher.client;

/** Generic failure talking to the Eurostat API (unexpected status, malformed response, exhausted retries). */
public class EurostatApiException extends Exception {
    public EurostatApiException(String message) {
        super(message);
    }

    public EurostatApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
