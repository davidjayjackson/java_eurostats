package com.eurostat.fetcher.parser;

import com.eurostat.fetcher.model.Observation;

import java.util.List;

/** Result of flattening a JSON-stat 2.0 response: the dimension order plus one row per observation. */
public class ParsedDataset {

    private final List<String> dimensionIds;
    private final List<Observation> observations;

    public ParsedDataset(List<String> dimensionIds, List<Observation> observations) {
        this.dimensionIds = dimensionIds;
        this.observations = observations;
    }

    public List<String> getDimensionIds() {
        return dimensionIds;
    }

    public List<Observation> getObservations() {
        return observations;
    }
}
