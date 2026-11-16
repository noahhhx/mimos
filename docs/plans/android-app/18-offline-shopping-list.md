# 18. Offline shopping list

**Depends on:** 10, 17 · **Review**

Shops have bad signal. Ticking the list should work without it.

## Decide first

This needs an owner decision and probably an ADR before it is built:

- What works offline: ticks only, or adding items too?
- Conflicts: two members tick the same list in the same shop, one offline.
  Last write wins per item is the simplest rule; is it right?
- Does the API need anything new (a batch of ticks with timestamps, or an
  idempotency key per change) so a queued change replays safely?

## Build (once decided)

- The list cached on the device when opened, a queue of changes persisted
  locally, replayed in order when the network returns, each replay
  idempotent.
- The screen says when it is offline and how many changes wait.

## Checks

- Flow: `shopping-offline` (airplane mode via `adb`, tick, reconnect,
  the server has the ticks).
- Gates: mobile, and backend if the API changes.
