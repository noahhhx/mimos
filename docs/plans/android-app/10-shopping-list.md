# 10. Shopping list

**Depends on:** 08

The week's shopping list (ADR-0027), usable one-handed in a shop.

## Build

- The list by aisle, tick and untick, generate and regenerate, restore.
- Add an item (name, optional quantity, unit from the recipe form's metric
  units, pieces by default) and remove added items; generated items have
  no Remove.
- Large tap targets; the screen stays awake while the list is open.
- Ticks are sent at once; a failed tick shows as unsent with a retry. Real
  offline support is item 17.

## Checks

- Flow: `shopping-list` (generate from a planned week, tick, add an item,
  regenerate keeps it, remove it).
- Gates: mobile.
