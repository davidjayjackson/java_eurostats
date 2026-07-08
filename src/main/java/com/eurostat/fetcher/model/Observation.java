package com.eurostat.fetcher.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One flattened Eurostat observation: a value plus its dimension coordinates (geo, time, unit, ...). */
public class Observation {

    private final Map<String, String> dimensions = new LinkedHashMap<>();
    private final double value;

    public Observation(double value) {
        this.value = value;
    }

    public void putDimension(String id, String code) {
        dimensions.put(id, code);
    }

    public String getDimension(String id) {
        return dimensions.get(id);
    }

    public Map<String, String> getDimensions() {
        return dimensions;
    }

    public double getValue() {
        return value;
    }
}
