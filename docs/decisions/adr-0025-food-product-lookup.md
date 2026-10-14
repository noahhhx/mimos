# ADR-0025: Food product lookup by barcode

- Status: Accepted
- Date: 2026-10-14

## Context

People log packaged food (a yoghurt, a can of cola, a protein bar) as
often as meals they cook. Typing its name and four nutrition values from
the label is slow, and the label's numbers already exist in public
product databases keyed by the barcode on the pack. The Log page should
let someone scan that barcode and log the product. This ADR covers the
backend half: looking a barcode up and answering a product. A later
change adds the scanner to the Log page on top of it.

## Decision

- **Open Food Facts is the source.** It is a worldwide, crowd-sourced
  database of packaged food, open data under the Open Database License
  (ODbL), free to read, and needs no key or account. Commercial databases
  need keys, contracts, or per-lookup fees, none of which a self-hoster
  should need.
- **The API looks the barcode up, not the browser.**
  `GET /api/v1/food-products/{barcode}` (`lookUpFoodProduct`) calls Open
  Food Facts from the server, for these reasons:
  - Open Food Facts asks every client to name itself in a `User-Agent`
    (`Mimos/<version> (https://github.com/noahhhx/mimos)`), which a
    browser cannot set.
  - Open Food Facts never sees the person's IP address or browser, only
    the server's and the barcode.
  - The instance switch (`mimos.open-food-facts.enabled`) is enforced in
    one place: off, the endpoint is a 404 and the client bean does not
    exist, so no barcode leaves the server.
  - Harness scenarios run against a fake (`open-food-facts-fake` in
    `deploy/docker/compose.fakes.yml`), as intervals.icu's do.
  - Open Food Facts' JSON is parsed at the edge, in
    `integrations/open-food-facts`. Its types are package-private, and
    the module answers its own `FoodProduct`.
  - Agents get the lookup as an MCP tool for free (ADR-0014).
- **What a lookup returns.** A barcode is 8 to 14 digits (EAN-8, UPC-A,
  EAN-13, GTIN-14), checked before any call; anything else is a 400. A
  found product is its `name` (`product_name`, else `generic_name`), its
  first `brand`, a `basis` of `PER_100_G` or `PER_100_ML`, its
  `nutrition` per basis (each of calories, protein, carbs, and fat
  absent when Open Food Facts lacks it, never zero), and a `servingSize`
  in the basis's unit when Open Food Facts gives a numeric one. An
  unknown barcode, or a product with no name at all, is a 404. Open Food
  Facts not answering, answering an error (a 5xx, a 429), or answering
  unreadable JSON is a 502 (`OpenFoodFactsUnavailableException`). The
  client waits at most 5 seconds to connect and 5 to read.
- **Nothing is stored.** No cache, no table, no migration. Logging the
  product goes through the existing ad-hoc meal log, which keeps only
  the description and the totals the person saves.
- **Attribution.** ODbL requires attributing the data. Wherever the web
  shows a looked-up product, it says "From Open Food Facts".
- **The module depends on no core module.** `FoodProduct` has its own
  two-value basis and nutrition record. Reusing `core-recipes`'
  `NutritionBasis` would allow `PER_PIECE`, which a lookup never answers,
  and would pull a module with JDBC persistence and picture storage into
  an HTTP client. The API maps it to the contract's `NutritionBasis` and
  `Nutrition` like every other controller.

### What the live API answers

Checked with curl against `https://world.openfoodfacts.org` on
2026-10-14, for Nutella 3017620422003, Coca-Cola 5449000000996, Coca-Cola
(Sweden) 5000112637922, Marmite 50184453, Oatly iKaffe 7394376616037,
Cristaline water 3274080005003, a nameless product 6111246950845, and
unknown codes. The module's test fixtures are these answers, trimmed.

- **Found:** HTTP 200, `status: 1`, `product`. **Unknown:** HTTP 404
  with `{"status": 0, "status_verbose": "product not found"}`. A code it
  considers invalid (0000000000017) is HTTP 200 with `status: 0`. Both
  are a 404 from Mimos.
- **Rate limit:** past about 100 product reads a minute it answers HTTP
  429 with an nginx HTML page, not JSON.
- **Normalised codes:** a 12-digit UPC-A (049000028911) comes back as
  `code: "0049000028911"`. Mimos answers the barcode as it was asked.
- **Nutrition keys:** `energy-kcal_100g`, `proteins_100g`,
  `carbohydrates_100g`, and `fat_100g` hold the label's values per 100 g,
  or per 100 ml for liquids. Mimos reads only these `_100g` keys, never
  `_serving` and never `nutriments_estimated` (Open Food Facts' own
  guesses from ingredients, which it returns even when not asked for).
- **Energy:** `energy_100g` is always kilojoules, whatever unit was
  entered; `energy-kj_100g` is sometimes missing (Marmite has only
  `energy-kcal_100g` and `energy_100g`). When `energy-kcal_100g` is
  missing, Mimos derives calories from `energy-kj_100g`, else from
  `energy_100g`, divided by 4.184. Every value is rounded to one decimal
  place, since computed values arrive as 52.941176470588.
- **Per 100 ml:** `nutrition_data_per` says `100g` for most drinks
  (both Coca-Colas, Oatly, water), so it cannot tell a drink apart on its
  own; only some products say `100ml`. A product counts as per 100 ml
  when `nutrition_data_per` is `100ml`, `product_quantity_unit` is `ml`,
  or `serving_quantity_unit` is `ml`. `product_quantity_unit` came back
  only when `product_quantity` was in `fields` too, so the client asks
  for both.
- **Serving:** `serving_quantity` is a number (sometimes absent, and
  older records hold strings, so strings of digits are read too), with
  `serving_quantity_unit`. Coca-Cola (Sweden) is a 330 ml can whose
  serving was entered as 330 g; a serving in another unit than the basis
  is left out rather than guessed.
- **Missing values:** a key is simply absent (Marmite has no
  `fat_100g`; the water has no energy or macros at all). Empty strings
  stand for no value in names and brands (6111246950845 has
  `product_name: ""` and `generic_name: ""`).
- **Brands:** `brands` is one comma-separated string
  (`"Nutella, Ferrero"`); Mimos takes the first.
- **`fields`:** a comma-separated list cuts the answer to those product
  fields, though some (`nutrition_data`, `nutriments_estimated`) come
  back regardless.

## Consequences

- A lookup needs outbound HTTPS from the API to
  `world.openfoodfacts.org`. A self-hoster who does not want any barcode
  sent out sets `MIMOS_OPENFOODFACTS_ENABLED=false`.
- Every lookup is a live call: no cache means no stale data and nothing
  to expire, at the cost of a round trip (under 0.1 s measured) and of
  sharing Open Food Facts' 100-a-minute limit across one instance's
  users. A cache is the first thing to add if that limit is ever hit.
- Data quality is Open Food Facts'. Values can be wrong or missing, so
  the scan UI should show what it found and let the person correct it
  before logging.
- `contracts/api/openapi.yaml` is version 0.17.0. The scan UI calls
  `lookUpFoodProduct` from `@mimos/api-client`.
