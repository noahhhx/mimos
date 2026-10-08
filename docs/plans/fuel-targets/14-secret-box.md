# 14. Encrypt the API key

**Step:** 14.5 · **Depends on:** 12

## Build

- `SecretBox` in `integrations/intervals-icu`: AES-GCM under
  `MIMOS_SECRET_KEY`, 32 bytes in base64, with a fresh 96-bit nonce from
  the injected `SecureRandom` for every encryption. It returns ciphertext
  and nonce; decrypting with the wrong key fails with a distinct
  exception, never garbage.
- No key set: the intervals.icu feature is off, as if
  `mimos.intervals-icu.enabled` were false. A key that is not 32 bytes of
  base64 fails startup, as static plugin misconfiguration does.
- `deploy/docker/compose.yml` sets a development value.
  `deploy/selfhost/compose.yml` passes the variable through, and
  `deploy/selfhost/.env.example` asks the operator to generate one with
  `openssl rand -base64 32`.

## Checks

- Tests: a round trip, a fresh nonce per encryption (two encryptions of
  one key differ), a wrong key failing, no key turning the feature off, a
  malformed key failing startup.
- Gates: Java; the self-host `config -q` check from AGENTS.md.

Land on `main`; set row 14 to `done`.
