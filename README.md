<p align="center">
  <img src="packaging/banner.png" alt="Eurostat Data for Calc" width="820">
</p>

<p align="center">
  <a href="https://github.com/davidjayjackson/java_eurostats/releases/latest"><img src="https://img.shields.io/github/v/release/davidjayjackson/java_eurostats" alt="Release"></a>
</p>

A LibreOffice Calc add-in that adds a spreadsheet function, `EUROSTATDATA`, for pulling data
straight from the [Eurostat](https://ec.europa.eu/eurostat) REST API (JSON-stat 2.0 format)
into a sheet.

```
=EUROSTATDATA("nama_10_gdp"; "geo=DE;time=2023;unit=CP_MEUR")
```

returns a table that spills into the sheet: a header row (one column per dimension, plus
`value`), followed by one row per observation.

On failure (unknown dataset code, rate limiting, network error) the function returns a single
cell containing a readable `#ERROR: ...` message instead of a cryptic `Err:5xx`.

## Requirements

- A LibreOffice install with Java enabled (**Tools ▸ Options ▸ LibreOffice ▸ Advanced ▸ Use a
  Java runtime environment**).
- A JDK to build from source (JDK 8+ is enough — the code is deliberately Java 8-compatible and
  has **no external dependencies**: no Jackson, no Maven, nothing to download).
- The LibreOffice SDK matching your LibreOffice install (ships with LibreOffice, or install the
  `libreoffice-sdk` package). `build.sh` expects it at `<LibreOffice install>/sdk`.

## Building

```bash
./build.sh
```

This runs the LibreOffice SDK's own toolchain end to end — `unoidl-write` (compiles the custom
UNO interface), `javamaker` (generates its Java stub), `javac`, `jar`, then zips everything into
`build/EurostatAddin.oxt`. No Maven, no network access needed to build.

Edit the `LO_HOME` and `JDK` variables at the top of `build.sh` if your LibreOffice or JDK live
somewhere other than `/home/davidj/libreoffice26.2` and `/home/davidj/jdks/jdk8u492-b09`.

## Installing

**Prebuilt:** grab `EurostatAddin-1.0.7.oxt` from the
[v1.0.7 release](https://github.com/davidjayjackson/java_eurostats/releases/tag/v1.0.7) — no build
step needed.

**From source:** build it yourself with `./build.sh` (see above), which produces the same file at
`build/EurostatAddin.oxt`.

Either way, install it by double-clicking the `.oxt` to open it in the Extension Manager, or from
a terminal:

```bash
/path/to/libreoffice/program/unopkg add EurostatAddin-1.0.7.oxt
```

Restart Calc if it was already open. The function then appears under the **Eurostat** category
in the Function Wizard.

## Demo

`demo/Eurostat-Demo.ods` (also attached to the
[v1.0.7 release](https://github.com/davidjayjackson/java_eurostats/releases/tag/v1.0.7)) is a real
spreadsheet with several `EUROSTATDATA(...)` formulas already entered and computed against live
Eurostat data — open it to see the function working without typing anything (it needs the add-in
installed first, see above, or the formulas show `#NAME?`). It also demonstrates the error path
with an unknown dataset code.

It was generated with `tools/BuildDemoSheet.java`, which drives a headless Calc instance over
the UNO API to enter the formulas, recalculate, and save the file — useful as a worked example of
scripting Calc from Java, and to regenerate the demo after changing the add-in.

## Usage

```
=EUROSTATDATA(datasetCode; filters)
```

- `datasetCode` — a Eurostat dataset code, e.g. `nama_10_gdp`.
- `filters` — optional dimension filters as `key=value` pairs separated by `;`, e.g.
  `"geo=DE;time=2023;unit=CP_MEUR"`. Pass `""` for no filtering. See the
  [filters cheatsheet](docs/eurostat-filter-cheatsheet.pdf) for syntax, gotchas, reserved
  parameters, and common dimension keys.

Enter it as an array formula so the whole table spills out: select a range large enough for the
result, type the formula, and confirm with **Ctrl+Shift+Enter** (recent LibreOffice versions with
dynamic array support will spill automatically from a single cell).

### Examples

```
=EUROSTATDATA("nama_10_gdp"; "geo=DE;time=2023;unit=CP_MEUR")
=EUROSTATDATA("une_rt_a"; "geo=FR;time=2023")
=EUROSTATDATA("tec00118"; "geo=DE;sinceTimePeriod=2015")
```

The last example uses `sinceTimePeriod`, one of Eurostat's own reserved query parameters (not a
dimension) — useful for pulling a whole year range back in one call. `lastTimePeriod` works the
same way. Any filter key you pass goes straight into the API query string.

## Common dataset codes

| Code | Description |
|------|-------------|
| `nama_10_gdp` | GDP and main components |
| `une_rt_a` | Unemployment rate (annual) |
| `demo_pjan` | Population on 1 January |
| `prc_hicp_manr` | HICP inflation (annual rate, monthly) |
| `tec00114` | Real GDP growth rate |
| `tec00118` | HICP inflation (annual rate, yearly) |

Browse the full catalogue at the [Eurostat Data Browser](https://ec.europa.eu/eurostat/databrowser/).

## Project layout

```
idl/org/libreoffice/eurostat/addin/XEurostatAddin.idl   custom UNO interface for the function
src/main/java/org/libreoffice/eurostat/addin/           the UNO add-in component
src/main/java/com/eurostat/fetcher/client/               HTTP client (HttpURLConnection) with retry/backoff
src/main/java/com/eurostat/fetcher/json/                 hand-rolled JSON parser (no external deps)
src/main/java/com/eurostat/fetcher/parser/                JSON-stat 2.0 -> flat observation list
src/main/java/com/eurostat/fetcher/model/                 Observation POJO
packaging/                                                 .oxt manifest/description sources
build.sh                                                   builds build/EurostatAddin.oxt
test/SmokeTest.java                                        calls the add-in logic directly (no soffice needed)
test/LiveCalcTest.java                                     end-to-end check against a real headless Calc instance
```

## Notes

- The Eurostat API is free and requires no API key.
- Retries (429/5xx) use exponential backoff capped at a few seconds, since a Calc formula
  recalculating blocks the UI while it runs — this isn't tunable from the sheet, only in
  `EurostatClient`.
- Large datasets may need additional dimension filters to stay within response size limits.

## License

[MIT](LICENSE)
