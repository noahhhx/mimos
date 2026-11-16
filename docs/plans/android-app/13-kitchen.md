# 13. Kitchen home

**Depends on:** 08, 09, 10

The first screen after sign-in, as the web's `/app` page
(`apps/web/src/app/app/page.tsx`).

## Build

- The greeting, This week and Tonight (meals the caller eats only),
  the plugins' "A thought for …" suggestions, and Still to buy from the
  week's list, each linking to its screen.
- Nothing new on the API: the same reads as the web's page.

## Checks

- Flow: `kitchen-home` (a planned dinner tonight shows under Tonight,
  an unticked item under Still to buy).
- Gates: mobile.
