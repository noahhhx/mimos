# 13. Household and invites

**Depends on:** 05

The Household page and joining (ADR-0019).

## Build

- Members, leave the household (with the web's warnings about what stays
  and what goes).
- Create an invite and share its link with the Android share sheet.
- Join: the invite link is the web's `/app/join/{token}` on the
  instance's domain. The app accepts a pasted link, and the web's join
  page gains an "Open in the app" link through the app's scheme
  (`io.github.noahhhx.mimos://join/{token}`), so tapping an invite on a
  phone with the app lands in its join screen. The join screen shows the
  invite preview and what joining does, as the web does.
- An invite for another instance than the connected one says so and
  offers to switch.

## Checks

- Flows: `household-invite` with `test` creating and a fresh account
  joining by pasted link, and the scheme link opened with `adb`.
- Web: the join page's link, covered in its harness scenario.
- Gates: mobile, web.
