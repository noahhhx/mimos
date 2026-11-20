# 14. Household and invites

**Depends on:** 06

The Household page and joining (ADR-0019).

## Build

- Members, leave the household (with the web's warnings about what stays
  and what goes).
- Create an invite and share its link with the Android share sheet.
- Join. An invite link is the web's `/app/join/{token}` on the
  instance's domain, which the app cannot claim. The app accepts a pasted
  link, and the web's join page gains an "Open in the app" link,
  `io.github.noahhhx.mimos://join?instance=<web address>&token=<token>`
  (the app-link scheme, not the sign-in one). The join screen shows the
  invite preview and what joining does, as the web does.
- An invite for another instance than the connected one says so and
  offers to switch.

## Checks

- Flow: `household-invite` with one account creating and a fresh one
  (`harness user create`) joining by pasted link, and by the scheme link
  opened with `adb shell am start`.
- Web: the join page's link, covered in its harness scenario.
- Gates: mobile, web.
