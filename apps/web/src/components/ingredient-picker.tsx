import { useId, useState } from "react";

import type { CatalogIngredient } from "@mimos/api-client";

import { describeEntry, searchCatalog } from "@/lib/catalog-match";

/**
 * An ingredient line's name, typed into a search over the ingredients the
 * user can count (ADR-0016). Picking one links the line; when nothing
 * fits, the last option adds what was typed as a new ingredient of the
 * user's own.
 */
export function IngredientPicker({
  label,
  name,
  catalog,
  onType,
  onPick,
  onAdd,
}: {
  label: string;
  name: string;
  catalog: CatalogIngredient[];
  onType: (name: string) => void;
  onPick: (entry: CatalogIngredient) => void;
  onAdd: (name: string) => void;
}) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  const matches = searchCatalog(name, catalog);
  const typed = name.trim();
  const exact = matches.some((entry) => entry.name.toLowerCase() === typed.toLowerCase());
  const options: ({ kind: "entry"; entry: CatalogIngredient } | { kind: "add" })[] = [
    ...matches.map((entry) => ({ kind: "entry" as const, entry })),
    ...(typed !== "" && !exact ? [{ kind: "add" as const }] : []),
  ];
  const showing = open && options.length > 0;

  const choose = (index: number) => {
    const option = options[index];
    if (!option) {
      return;
    }
    setOpen(false);
    if (option.kind === "entry") {
      onPick(option.entry);
    } else {
      onAdd(typed);
    }
  };

  return (
    <div className="ingredient-picker">
      <label>
        Ingredient
        <input
          role="combobox"
          aria-label={label}
          aria-expanded={showing}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showing ? `${listId}-${active}` : undefined}
          value={name}
          maxLength={200}
          placeholder="Search ingredients"
          autoComplete="off"
          onChange={(e) => {
            onType(e.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
              e.preventDefault();
              setOpen(true);
              const step = e.key === "ArrowDown" ? 1 : -1;
              setActive((current) => (current + step + options.length) % Math.max(options.length, 1));
            } else if (e.key === "Enter" && showing) {
              e.preventDefault();
              choose(active);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
        />
      </label>
      {showing && (
        <ul className="picker-options" role="listbox" id={listId}>
          {options.map((option, index) => (
            <li
              key={option.kind === "entry" ? option.entry.slug : "add"}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? "active" : undefined}
              // Choosing on mouse down, before the input's blur closes the list.
              onMouseDown={(e) => {
                e.preventDefault();
                choose(index);
              }}
              onMouseEnter={() => setActive(index)}
            >
              {option.kind === "entry" ? (
                <>
                  <span>{option.entry.name}</span>
                  <span className="muted">
                    {option.entry.isShared ? "" : "Yours · "}
                    {describeEntry(option.entry)}
                  </span>
                </>
              ) : (
                <span>+ Add &ldquo;{typed}&rdquo; as a new ingredient</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
