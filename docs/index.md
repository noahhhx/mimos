---
hide:
  - navigation
  - toc
---

<div class="mimos-hero" markdown>

<p class="mimos-eyebrow">Documentation</p>

# Mimos

<p class="mimos-lede">A recipe companion for people who cook: a curated library of free
recipes, your own recipes with the same richness, and a straight line from
"what am I cooking this week?" to plans, shopping lists, and a picture of
what you're actually eating.</p>

[Get started](guide/index.md){ .md-button .md-button--primary }
[Self-host Mimos](self-hosting.md){ .md-button }

</div>

Mimos works the same whether you sign up on someone else's server or run
it on your own hardware. This guide covers both.

## Where to start

<div class="grid cards" markdown>

-   :material-silverware-fork-knife: **[Using Mimos](guide/index.md)**

    ---

    Sign in, find your way around, and plan your first week.

-   :material-book-open-variant: **[Recipes](guide/recipes.md)**

    ---

    Cook from the free library, or write and keep your own recipes.

-   :material-calendar-week: **[Planning a week](guide/planning.md)**

    ---

    Fill the week's meals from the library and your own recipes.

-   :material-cart-outline: **[Shopping list](guide/shopping-list.md)**

    ---

    Turn the week's plan into one list, grouped by aisle.

-   :material-server-outline: **[Self-hosting](self-hosting.md)**

    ---

    Run Mimos on your own server from the published images, behind your
    own HTTPS proxy.

-   :material-puzzle-outline: **[Writing a plugin](plugins/index.md)**

    ---

    Build an add-on that suggests meals for the week, like Country of the
    Week.

</div>

## Try it on your machine

With Docker installed, this brings up a complete Mimos with a demo account:

```bash
git clone https://github.com/noahhhx/mimos.git
cd mimos/deploy/docker
docker compose up -d --wait
```

Open <http://localhost:3000> and sign in as `test` with the password
`mimos-test`. This setup is for trying Mimos out; to run it for real, follow
[Self-hosting](self-hosting.md).

## Free software

Mimos is free software under the GNU GPL v3. The source, issue tracker, and
contributor docs live on
[GitHub](https://github.com/noahhhx/mimos).
