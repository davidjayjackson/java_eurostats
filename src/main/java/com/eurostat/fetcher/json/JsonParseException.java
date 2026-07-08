package com.eurostat.fetcher.json;

/** Thrown when {@link Json#parse(String)} encounters malformed JSON. */
public class JsonParseException extends Exception {
    public JsonParseException(String message) {
        super(message);
    }
}
