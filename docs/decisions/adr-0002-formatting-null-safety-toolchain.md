# ADR-0002: Formatting and null-safety toolchain

- Status: accepted
- Date: 2026-08-24

## Context

The backend is a multi-module Maven build written by agents as much as by
humans. Two failure modes needed a mechanical answer rather than review
discipline:

- **Style drift.** Different changes formatting differently (indent width,
  import order, POM layout) creates noisy diffs and hides real changes.
- **Null handling.** Java's implicit nullability (everything may be `null`)
  pushes NPE discovery to runtime, where tests are weakest.

The same setup is proven in `vikunja-integrations`; this adopts it with one
addition: POM formatting.

## Decision

- **Spotless** (`spotless-maven-plugin`) formats all modules from the root
  POM:
  - Java via **palantir-java-format** (4-space indent, 120 columns), plus
    import ordering (`java`, `javax`, `jakarta`, everything else, static
    last), unused-import removal, trailing-whitespace trim, final newline.
  - Every `pom.xml` via **sortPom** (4-space indent, recommended element
    order, blank lines kept, empty elements self-closed without a space).
  - `spotless:apply` runs automatically at `process-sources` on every
    build, so a normal `./mvnw verify` formats as a side effect; CI gates
    with `spotless:check` before `verify`.
- **NullAway on Error Prone** enforces **JSpecify** nullness at compile
  time across all modules:
  - Only nullness checks gate the build: Error Prone runs with
    `-XepDisableAllChecks`, then `NullAway` and
    `RequireExplicitNullMarking` at `ERROR`, in JSpecify mode restricted
    to `@NullMarked` code.
  - Every package carries a `@NullMarked` `package-info.java`; the
    annotation dependency (`org.jspecify:jspecify`) is non-optional with
    runtime retention so reflective access to `@Nullable`-bearing records
    keeps working inside Boot's fat jar.
  - `RequireExplicitNullMarking` means a new package without a
    `package-info.java` fails the build — silence cannot be mistaken for
    "checked and safe".
- Error Prone's access to javac internals comes from `.mvn/jvm.config`
  (`--add-exports`/`--add-opens`), picked up by `mvnw` and by the Docker
  build stage (the Dockerfile copies `.mvn/`). Because the build targets
  JDK 21, javac also gets `-XDaddTypeAnnotationsToSymbol=true` — Error
  Prone requires it on JDK 21 to keep type annotations on symbols; it is
  unneeded but harmless on JDK 22+.

## Consequences

- Formatting is not a review topic: unformatted code never reaches CI's
  `verify`, and `./mvnw spotless:apply` is always the fix.
- POMs get a uniform shape (4-space, sorted top-level elements); sorting is
  structural only — dependency order is preserved to keep diffs stable.
- Nullability is part of the type system from day one: APIs are non-null by
  default and nullable spots must say so with `org.jspecify.annotations
  .Nullable` (type-use placement).
- `@NullUnmarked` remains the escape hatch for un-checkable code; it must
  be justified rather than default.
- Adding an annotation processor later (Lombok, configuration metadata)
  means extending `annotationProcessorPaths` in the root POM — with that
  list set, the classpath is not scanned for processors.
- `error-prone.version` bumps require re-checking `.mvn/jvm.config` against
  the Error Prone install docs.
