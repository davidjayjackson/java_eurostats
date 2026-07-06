# Eurostat Data Fetcher — Claude Code Prompt

A ready-to-use prompt for Claude Code that generates a Java application to pull data from the Eurostat API.

## The Prompt

> Build a Java application that pulls data from the Eurostat API. Requirements:
>
> 1. Use the Eurostat REST API (the JSON-stat 2.0 endpoint at `https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/{dataset_code}`).
>
> 2. Accept these parameters:
>    - `dataset_code` (e.g., `nama_10_gdp` for GDP data)
>    - Optional filters as key-value pairs (e.g., `geo=DE`, `time=2023`, `unit=CP_MEUR`)
>    - Output format (`csv` or `json`)
>
> 3. Handle the JSON-stat response format: parse the dimension structure and flatten it into a tabular model with one record per observation, with fields for each dimension (geo, time, unit, etc.) plus a `value` field.
>
> 4. Include:
>    - Retry logic with exponential backoff for failed requests
>    - Clear error handling for invalid dataset codes (404) and rate limiting (429)
>    - A CLI (using `picocli` or plain `args` parsing) so I can run it from the terminal
>    - Logging (SLF4J + Logback) of what's being fetched and how many observations were returned
>
> 5. Set it up as a Maven project (`pom.xml`) with a runnable JAR, and include a short README with usage examples.
>
> Use `java.net.http.HttpClient` for requests and Jackson (`com.fasterxml.jackson`) for JSON parsing. Define a POJO for the observations and write the CSV output with a simple writer or `opencsv`. Start by fetching a small example dataset to confirm the parsing works, then generalize.

## How to Use

1. Open Claude Code in your project directory.
2. Paste the prompt above.
3. Let Claude Code scaffold the Maven project, then build and run it.

## What It Builds

A command-line Java tool that:

- Queries the Eurostat dissemination API using the JSON-stat 2.0 format
- Flattens multi-dimensional statistical data into tidy records
- Filters by dimensions such as country (`geo`), year (`time`), and unit
- Exports results to CSV or JSON
- Handles network failures, invalid dataset codes, and rate limits gracefully

## Requirements

- Java 17 or later
- Maven 3.6+
- Internet access to `ec.europa.eu`

## Example Usage (after building)

```bash
# GDP for Germany in 2023, current prices in millions of euro
java -jar target/eurostat-fetcher.jar \
  --dataset nama_10_gdp \
  --filter geo=DE --filter time=2023 --filter unit=CP_MEUR \
  --format csv

# Unemployment rate, output as JSON
java -jar target/eurostat-fetcher.jar \
  --dataset une_rt_a \
  --filter geo=FR --filter time=2023 \
  --format json
```

## Common Dataset Codes

| Code | Description |
|------|-------------|
| `nama_10_gdp` | GDP and main components |
| `une_rt_a` | Unemployment rate (annual) |
| `demo_pjan` | Population on 1 January |
| `prc_hicp_manr` | HICP inflation (annual rate) |
| `tec00114` | Real GDP growth rate |

Browse the full catalogue at the [Eurostat Data Browser](https://ec.europa.eu/eurostat/databrowser/).

## Notes

- The Eurostat API is free and requires no API key.
- Large datasets may need additional dimension filters to stay within response size limits.
