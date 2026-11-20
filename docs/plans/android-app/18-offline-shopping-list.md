# 18. Offline shopping list

**Depends on:** 10, 17 · **Review**

Shops have bad signal. Ticking the list should work without it.

## Decided

[ADR-0034](../../decisions/adr-0034-offline-shopping-list.md) settles the
three questions this item asked, by default under the owner's grant and
waiting for the owner's review:

- Ticks work offline; adding, removing, and generating do not.
- Last write wins per item, in the order the server receives them. A tick
  sets `checked` to a value, so replaying it is idempotent.
- No API change. A replayed tick for an item the server no longer has gets
  a 404 and is dropped; every other failure keeps it waiting.

## Built

- `ShoppingStore` (`commonMain`) keeps two text entries, the lists and the
  pending ticks, so a list a newer app wrote cannot cost a tick. Android
  keeps them in the `shopping` shared-preferences file
  (`SharedPreferencesShoppingStore`), excluded from backups and device
  transfers with the session. Lists are kept per Monday, the four loaded
  last; ticks as item id, Monday, and wanted state, in the order first
  tapped. Anything that does not decode counts as nothing stored.
- `ShoppingModel.ticks` is a map from item to `Tick(monday, checked,
  status)`, the status being `Queued`, `Sending`, or `Unsent`. One sending
  loop takes queued ticks in order, one at a time. A sent tick updates the
  shown and stored list; a 404 drops the tick and loads the week again; any
  other failure (no answer, a 401, a 5xx) marks it and every queued tick
  `Unsent`, and ends the round.
- `NetworkWatcher` (`commonMain`, a `StateFlow<Boolean>`) is online while
  Android has a default network (`AndroidNetworkWatcher`,
  `registerDefaultNetworkCallback`, `ACCESS_NETWORK_STATE`). A network that
  is not validated still counts, for a home instance on a LAN with no
  internet. While offline a tap is `Unsent` and nothing is sent on its own.
- Ticks are sent when the phone regains a network, when the list loads,
  and when the session is renewed, each only while online
  (`ShoppingModel.reconnected`, called from `AppModel`), and on **Send
  now**, which always tries. A network returning while the session is
  Unreachable calls `SessionModel.retry()`.
- `ListState.Loaded` carries `ListCopy.Server` or `ListCopy.Stored`. A load
  that cannot reach the server shows the stored copy of that week, or fails
  as before when there is none. A shown list stays on screen while it loads
  again.
- The banner above the list (`sendingNotice` in `ShoppingRules.kt`): "Offline.
  1 tick waiting to send." with no network; "N ticks not sent yet." with
  **Send now** when online; "This is the list as it was last loaded." added
  for a stored copy, or "Mimos could not be reached." with **Try again**
  when nothing waits. Each waiting item keeps its "Not sent"; item 10's
  per-item Retry is gone in favour of **Send now**.
- The No answer page offers **Shopping list**: the stored list to tick,
  with **← Back** (`ShoppingWithoutSession`). Its banner action renews the
  session, which then sends the ticks.
- `ShoppingModel.reset()` also clears the store, so sign-out (a refused
  session included), a household change, an import, and changing instance
  forget the stored lists and ticks.

## Checks

- `commonTest`: `ShoppingModelTest` (ticks offline are stored and not sent;
  the network back sends each once, in order, and updates the stored list;
  a 404 drops a tick; a 401 or no answer keeps it; a new model from the
  same store restores ticks and list, the app-killed case; a load with no
  answer shows the stored copy; a week never loaded still fails; nothing
  sends on its own while offline; reset clears the store; four weeks at
  most), `ShoppingRulesTest` (the banner), and `AppModelTest` (an
  Unreachable session retries when the network returns). Each was checked
  against a mutation of the behaviour it names.
- Flow `shopping-offline`: airplane mode on, tick, the banner, kill and
  relaunch, the tick and banner are still there, airplane mode off, the
  banner goes, and **Regenerate** keeps the item ticked, so the server has
  it. Airplane mode does not cut `adb reverse` (see "Found along the way"),
  so this proves the network gate and persistence, not a real outage.
- By hand, a real outage at launch: with the reverse rules for 8080 and
  8081 removed, the app showed No answer, opened the stored list, and took
  a tick as "Not sent"; with the rules back, **Try again** signed in and
  sent it (the server had the item checked).
- Gates: mobile; flows `shopping-offline`, `shopping-list`,
  `connect-and-sign-in`, and `stay-signed-in`; the parity check; docs.
