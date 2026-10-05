# Web fonts

The two typefaces of the Evening Kitchen design (`docs/design/index.md`),
committed so the product never fetches fonts from a font host at build or
run time. `src/app/layout.tsx` loads them with `next/font/local`; the
Keycloak theme copies them in (`deploy/keycloak/Dockerfile`) and the docs
site links to them (`docs/assets/fonts/`).

| File | Family | Axes kept |
| ---- | ------ | --------- |
| `newsreader/Newsreader.woff2` | Newsreader, upright | `opsz` 6–72, `wght` 400–500 |
| `public-sans/PublicSans.woff2` | Public Sans, upright | `wght` 400–600 |

Both are SIL Open Font License 1.1; each family's `OFL.txt` sits next to
its file. A third font, for flags only, is described under "Flags" below.

## Source

The upstream variable TTFs from the `google/fonts` repository, at commit
`8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`:

- `https://raw.githubusercontent.com/google/fonts/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/newsreader/Newsreader[opsz,wght].ttf`
- `https://raw.githubusercontent.com/google/fonts/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/publicsans/PublicSans[wght].ttf`

## Rebuilding

The weight axes are narrowed to the weights the design uses, then each
font is subset to Google Fonts' "latin" range and packed as `woff2`. The
tools (`fonttools`, `woff2`) come from the devenv shell; run this there
(`devenv shell`) in an empty directory, after downloading the two TTFs
above into it:

```sh
LATIN="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"
fonttools varLib.instancer "Newsreader[opsz,wght].ttf" wght=400:500 -o Newsreader-limited.ttf
fonttools varLib.instancer "PublicSans[wght].ttf" wght=400:600 -o PublicSans-limited.ttf
pyftsubset Newsreader-limited.ttf --unicodes="$LATIN" --layout-features="*" --output-file=Newsreader.ttf
pyftsubset PublicSans-limited.ttf --unicodes="$LATIN" --layout-features="*" --output-file=PublicSans.ttf
woff2_compress Newsreader.ttf
woff2_compress PublicSans.ttf
```

Copy `Newsreader.woff2` and `PublicSans.woff2` here. Characters outside
the subset (arrows, "✕") fall back to the next font in the stack.

## Flags

`twemoji-flags/TwemojiFlags.woff2` draws country flags, so a plugin's flag
icon (ADR-0017) is a flag on every system; Windows otherwise shows two
letters. It is the flag subset of Mozilla's Twemoji COLR font: the 26
regional indicator symbols (U+1F1E6–1F1FF) and the 258 flags they form
through the font's `ccmp` ligatures. The graphics are Twemoji's, CC BY
4.0; `twemoji-flags/ATTRIBUTION.md` gives the credit and
`twemoji-flags/LICENSE.md` both licenses.

`src/app/layout.tsx` loads it first in both font stacks with a
`unicode-range` of U+1F1E6-1F1FF, so it draws flags and nothing else, and
without a preload, so a page downloads it only when it shows a flag.

Source, release v0.7.0 of `mozilla/twemoji-colr` (SHA-256
`6d90152ee0d29e82fe2a87793af5aa4b7ad13e6538360889e141e81ed299ee8e`):

- `https://github.com/mozilla/twemoji-colr/releases/download/v0.7.0/Twemoji.Mozilla.ttf`
- `https://raw.githubusercontent.com/mozilla/twemoji-colr/v0.7.0/LICENSE.md`

Rebuild it in the devenv shell, in a directory holding that TTF:

```sh
pyftsubset Twemoji.Mozilla.ttf --unicodes="U+1F1E6-1F1FF" --layout-features="*" --output-file=TwemojiFlags.ttf
woff2_compress TwemojiFlags.ttf
```

Copy `TwemojiFlags.woff2` into `twemoji-flags/`.
