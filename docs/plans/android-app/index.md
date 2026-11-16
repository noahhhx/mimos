# Android app: work items

Built to [ADR-0029](../../decisions/adr-0029-android-app.md). A native
Android app with every feature the web app has, for people who would
rather open an app than a browser tab. Kotlin Multiplatform with only the
Android target, Compose UI, no Google Play Services, connected to any
Mimos instance, and released as a signed APK on GitHub Releases. iOS is
not built, but nothing here may close it off.

The order is deliberate. The scaffold, the client, sign-in, the theme, and
the emulator harness come first, so every feature item after them lands
with a Maestro flow that CI runs. Feature items follow the web's pages.

## How to work an item

1. Take the first `todo` item whose dependencies are `done`. One item per
   session.
2. Read AGENTS.md, ADR-0029, and the item before writing code. A feature
   item also reads the ADRs its web page was built to; the app follows the
   same rules, and the web page is the reference for behavior and copy.
3. Build what the item says. If it turns out bigger than one session, split
   it here first: add the new item file and its row, then carry on with the
   smaller piece.
4. Run the item's checks, plus the gates below for what you touched. Only
   one stack runs at a time, because compose binds fixed host ports.
5. An item marked **Review** changes what people see in a way the web does
   not settle, or needs the owner's decision. Post its screenshots (or the
   question) and wait for the go before landing.
6. Land on `feat/android-app`: conventional commits, present tense
   (`feat(mobile): ...`), each commit builds. In the item's last commit, set
   its row below to `done` with the commit's short SHA.
7. Anything out of the item's scope goes in "Found along the way".

## Items

| #  | Item | Depends on | Status |
| -- | ---- | ---------- | ------ |
| 01 | [Scaffold, toolchain, and build gates](01-scaffold.md) | – | todo |
| 02 | [Generated client and instance discovery](02-client-and-discovery.md) | 01 | todo |
| 03 | [Sign-in and sign-up](03-sign-in.md) | 02 | todo |
| 04 | [Evening Kitchen in Compose](04-design-system.md) | 01 | todo |
| 05 | [Emulator harness, Maestro, and CI flows](05-harness-and-flows.md) | 03, 04 | todo |
| 06 | [Recipes and pictures](06-recipes.md) | 05 | todo |
| 07 | [Household ingredients](07-ingredients.md) | 06 | todo |
| 08 | [The week's plan](08-plan.md) **Review** | 06 | todo |
| 09 | [Plugins: page, suggestions, week panels](09-plugins.md) | 08 | todo |
| 10 | [Shopping list](10-shopping-list.md) | 08 | todo |
| 11 | [Log and barcode scanning](11-log-and-barcodes.md) | 08 | todo |
| 12 | [Fuel and intervals.icu](12-fuel.md) | 11 | todo |
| 13 | [Household and invites](13-household.md) | 05 | todo |
| 14 | [Account, settings, export and import](14-account.md) | 05 | todo |
| 15 | [Release: signing, versions, GitHub Releases](15-release.md) | 02, 05 | todo |
| 16 | [Parity audit and user guide](16-parity-and-docs.md) | 06–15 | todo |
| 17 | [Offline shopping list](17-offline-shopping-list.md) **Review** | 10, 16 | todo |

Items 06 to 14 are independent once their dependencies are done; they can
run in parallel in separate worktrees. Item 15 can land as soon as 05 has,
so testers get signed builds while features arrive.

## Gates

- Mobile: `cd apps/mobile && ./gradlew check assembleDebug` (unit tests,
  ktfmt through Spotless, Android lint, and the Google Play Services ban).
- Mobile flows: `devenv shell -- harness up`, then
  `devenv shell -- harness mobile flow <flow>` for the item's flows.
- Backend (items that touch it): `./mvnw -B verify`.
- Contract (items that touch it): `npm run generate -w @mimos/api-client`
  leaves no diff, and the mobile build regenerates its client cleanly.
- Web (items that touch it): `npm run typecheck -w @mimos/web`,
  `npm test -w @mimos/web`, `npm run build -w @mimos/web`.
- Harness: `npm run typecheck -w @mimos/harness`, `npm test -w @mimos/harness`.
- Docs: `devenv shell -- mkdocs build --strict`.

## Decided by default, open to the owner

The owner settled platforms (Android only), language (Kotlin), the
verification approach, and distribution (signed APK on GitHub Releases, no
Google Play Services). These were filled in by the planning agent and can
be overturned:

- `minSdk` 26 (Android 8.0), for older family phones.
- Phones and tablets, adapting by window size class.
- `apps/mobile` as the project root, `io.github.noahhhx.mimos` as the
  application id.
- The API client generated at build time, not committed.
- The visual design mirrors Evening Kitchen exactly, not Material defaults.
- App and server share one version and release on the same `v*` tag.
- Online only until item 17.
- Flows run on every PR in CI. If the emulator job costs too much (item 05
  measures it), move it to main and tags only.

## Found along the way

Nothing yet.
