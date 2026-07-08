package com.eurostat.fetcher.client;

/** Thrown when the Eurostat API returns 404 for a dataset code. */
public class DatasetNotFoundException extends EurostatApiException {
    public DatasetNotFoundException(String datasetCode) {
        super("Dataset not found: " + datasetCode);
    }
}
