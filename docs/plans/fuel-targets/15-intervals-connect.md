# 15. Connect and disconnect

**Step:** 14.5 · **Depends on:** 13, 14

## Build

- `V16__intervals_icu.sql`: `intervals_icu_connection` with profile id
  (primary key, cascading from `user_profile`), athlete id, ciphertext,
  nonce, status, last sync time, and last error.
- `GET /api/v1/me/intervals-icu`: athlete id, status, last sync time, last
  error. Never the key.
- `PUT /api/v1/me/intervals-icu` with athlete id and key: checks the key
  against the athlete record first and answers 400 problem-details when
  intervals.icu refuses it, storing nothing. Otherwise stores the key
  encrypted.
- `DELETE /api/v1/me/intervals-icu`: deletes the connection and the
  person's `INTERVALS_ICU` sessions (a new `FuelService` method) and keeps
  their manual ones.
- `PUT` and `DELETE` carry `x-mcp: false`; `McpEndpointTests` pins them.
- With the feature off (gate or no secret key), all of these answer 404.
- A key that no longer decrypts (the operator rotated
  `MIMOS_SECRET_KEY`) reads as a broken connection that asks for the key
  again.
- The account export leaves the connection out; no format change.

## Checks

- API tests against an in-JVM stub: connect, wrong key 400 with no row,
  read never holds the key, disconnect keeps manual sessions, gate off 404,
  undecryptable key reads as broken.
- `./mvnw -B -pl apps/api -am test -Dtest=McpEndpointTests`.
- Live against the fake: connect with `i1` and the good key; then search
  `GET /api/v1/me/intervals-icu`, the account export, `harness sql` on the
  table, and `harness logs` for the key string. It appears nowhere in
  plain text.
- Gates: Java, contract.

Land on `main`; set row 15 to `done`.
