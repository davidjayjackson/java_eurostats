package com.eurostat.fetcher.parser;

import com.eurostat.fetcher.client.EurostatApiException;
import com.eurostat.fetcher.model.Observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Flattens a JSON-stat 2.0 "dataset" response into one record per observation.
 *
 * JSON-stat encodes the N-dimensional value cube as a single flat array/object,
 * where the offset of a cell is the sum of each dimension's index multiplied by
 * the product of the sizes of all dimensions that come before it (the first
 * dimension in "id" varies fastest).
 *
 * Input is the plain {@code Map}/{@code List}/{@code String}/{@code Double} tree produced by
 * {@link com.eurostat.fetcher.json.Json#parse(String)} (no Jackson available in this build).
 */
public class JsonStatParser {

    @SuppressWarnings("unchecked")
    public ParsedDataset parse(Object rootValue) throws EurostatApiException {
        if (!(rootValue instanceof Map)) {
            throw new EurostatApiException("Response is not a JSON-stat 2.0 dataset object");
        }
        Map<String, Object> root = (Map<String, Object>) rootValue;

        Object idValue = root.get("id");
        Object sizeValue = root.get("size");
        Object dimensionValue = root.get("dimension");
        Object valueValue = root.get("value");

        if (!(idValue instanceof List) || !(sizeValue instanceof List)
                || !(dimensionValue instanceof Map) || valueValue == null) {
            throw new EurostatApiException(
                    "Response does not look like a JSON-stat 2.0 dataset (missing id/size/dimension/value)");
        }

        List<Object> idList = (List<Object>) idValue;
        List<Object> sizeList = (List<Object>) sizeValue;
        Map<String, Object> dimensionMap = (Map<String, Object>) dimensionValue;

        int n = idList.size();
        List<String> ids = new ArrayList<String>(n);
        for (Object idElem : idList) {
            ids.add(String.valueOf(idElem));
        }

        int[] sizes = new int[n];
        for (int i = 0; i < n; i++) {
            sizes[i] = asInt(sizeList.get(i));
        }

        List<List<String>> codesByDim = new ArrayList<List<String>>(n);
        for (int i = 0; i < n; i++) {
            codesByDim.add(resolveCategoryCodes(ids.get(i), dimensionMap.get(ids.get(i)), sizes[i]));
        }

        long[] multipliers = new long[n];
        if (n > 0) {
            multipliers[0] = 1;
        }
        for (int i = 1; i < n; i++) {
            multipliers[i] = multipliers[i - 1] * sizes[i - 1];
        }

        List<Observation> observations = new ArrayList<Observation>();
        if (valueValue instanceof List) {
            List<Object> valueList = (List<Object>) valueValue;
            for (int offset = 0; offset < valueList.size(); offset++) {
                Object v = valueList.get(offset);
                if (v == null) {
                    continue;
                }
                observations.add(buildObservation(offset, asDouble(v), ids, sizes, multipliers, codesByDim));
            }
        } else if (valueValue instanceof Map) {
            Map<String, Object> valueMap = (Map<String, Object>) valueValue;
            for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
                int offset = Integer.parseInt(entry.getKey());
                observations.add(buildObservation(offset, asDouble(entry.getValue()), ids, sizes, multipliers, codesByDim));
            }
        } else {
            throw new EurostatApiException("Unexpected 'value' node type in JSON-stat response: "
                    + valueValue.getClass().getSimpleName());
        }

        return new ParsedDataset(ids, observations);
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveCategoryCodes(String dimId, Object dimDefValue, int size) throws EurostatApiException {
        if (!(dimDefValue instanceof Map)) {
            throw new EurostatApiException("Missing dimension definition for '" + dimId + "'");
        }
        Map<String, Object> dimDef = (Map<String, Object>) dimDefValue;
        Object categoryValue = dimDef.get("category");
        if (!(categoryValue instanceof Map)) {
            throw new EurostatApiException("Missing 'category' for dimension '" + dimId + "'");
        }
        Map<String, Object> category = (Map<String, Object>) categoryValue;

        List<String> codes = new ArrayList<String>(Collections.nCopies(size, (String) null));
        Object indexValue = category.get("index");
        if (indexValue instanceof Map) {
            Map<String, Object> indexMap = (Map<String, Object>) indexValue;
            for (Map.Entry<String, Object> entry : indexMap.entrySet()) {
                codes.set(asInt(entry.getValue()), entry.getKey());
            }
        } else if (indexValue instanceof List) {
            List<Object> indexList = (List<Object>) indexValue;
            for (int j = 0; j < indexList.size(); j++) {
                codes.set(j, String.valueOf(indexList.get(j)));
            }
        } else {
            // Single-category dimensions may omit "index"; fall back to label key order.
            Object labelValue = category.get("label");
            if (labelValue instanceof Map) {
                Map<String, Object> labelMap = (Map<String, Object>) labelValue;
                int pos = 0;
                for (String key : labelMap.keySet()) {
                    if (pos >= size) {
                        break;
                    }
                    codes.set(pos++, key);
                }
            }
        }
        return codes;
    }

    private Observation buildObservation(long offset, double value, List<String> ids, int[] sizes,
                                          long[] multipliers, List<List<String>> codesByDim) {
        Observation obs = new Observation(value);
        for (int i = 0; i < ids.size(); i++) {
            int pos = (int) ((offset / multipliers[i]) % sizes[i]);
            String code = codesByDim.get(i).get(pos);
            obs.putDimension(ids.get(i), code);
        }
        return obs;
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }
}
