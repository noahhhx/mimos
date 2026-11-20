# ADR-0034: The app's shopping list works offline

- Status: Proposed (decided by default under the owner's grant, waiting
  for the owner's review)
- Date: 2026-11-20
- Settles: "Decide first" in `docs/plans/android-app/18-offline-shopping-list.md`
  (ADR-0029)

## Context

Shops have bad signal. Since item 10, the app's Shopping tab shows a tick
at once and sends it, and a tick the server did not take stays ticked as
"Not sent" with a Retry. That held only while the app stayed open: the
ticks lived in memory, the list was read from the server each time, and a
phone that killed the app in the shop came back to "No answer" (the
session could not be renewed) with no list at all.

Item 18 asked three questions: what works offline, how conflicts between
members resolve, and whether the API needs anything new for a safe replay.

## Decision

- **Ticks work offline; nothing else does.** Ticking and unticking work
  with no connection. Adding, removing, and generating still need one and
  fail with their existing messages. Ticking is what a shopper does in
  the aisle; something forgotten can be added at the till or at home, and
  an added item queued offline would need an id the server has not given
  it yet.
- **The list is kept on the phone.** Each week's list is stored on the
  device when it loads, at most four weeks, the most recently loaded kept.
  When a load cannot reach the server, the stored list shows with a note
  that it is offline. A week never loaded shows the load error as before.
- **Pending ticks are kept on the phone** until the server confirms them,
  one per item: an item's last tap is the one that waits, so a queue is
  a map from item to its wanted state. They survive the app being killed.
  They are sent one at a time, as since item 10, when the phone regains a
  network, when the list loads, when the session is renewed, and when the
  person taps "Send now".
- **Last write wins per item, in the order the server receives them.** A
  tick sets `checked` to a value (`updateShoppingListItem`), so replaying
  it is idempotent: sending it twice leaves the same state. Two members
  ticking the same item differently is rare and costs one item bought
  twice or not at all, which they will see on the list; a timestamped
  merge would need an API change to settle it.
- **No API change.** A replayed tick for an item the server no longer has
  (the list was regenerated, which changes item ids, or the item was
  removed) gets a 404 and is dropped, and the list loads again. Every
  other failure, a 401 included, keeps it waiting. The contract stays
  additive-only without a new field (ADR-0003).
- **The phone's network is a hint.** The app watches the default network
  (`ConnectivityManager`, behind a `commonMain` interface, which needs the
  normal `ACCESS_NETWORK_STATE` permission). While there is none, it does
  not try to send, and the screen says "Offline" with how many ticks wait.
  Having a network does not prove the instance answers, so a tick that
  fails while online waits too, and "Send now" always tries.
- **Offline at launch.** When the stored session cannot be renewed ("No
  answer"), that screen offers the shopping list as stored, to tick. The
  ticks wait for the session; once it is renewed (by "Try again", or
  automatically when the phone regains a network), they are sent.
- **Kept per person.** The stored lists and pending ticks belong to the
  person signed in. They are cleared whenever the in-memory ticks were:
  on sign-out (the session refused included), changing instance, a
  household change, and an import. A pending tick is lost then.

## Consequences

- A shopper can tick the whole list in a shop with no signal, and the app
  being killed meanwhile loses nothing.
- A tick made offline can land after another member's later change to the
  same item, and win.
- A regeneration by another member while a tick waits drops that tick.
  The server keeps ticks across regeneration only for ticks it has.
- The shopping list is the one thing the app keeps besides its settings
  and the sealed session. Like the session, it is left out of backups and
  device transfers: restored without the session it belongs to, it would
  only be cleared.
- Nothing here changes the web or the API.
