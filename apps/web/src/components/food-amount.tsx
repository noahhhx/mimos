"use client";

import { useState } from "react";

import { unitLabel } from "@/lib/log-entry";
import { MAX_FOOD_AMOUNT, parseFoodAmount, unitOf } from "@/lib/planned-item";

/**
 * A planned food's amount: a number with its unit beside it, saved on Enter
 * or when focus leaves. Anything the API would refuse goes back to what is
 * planned.
 */
export function FoodAmount({
  name,
  amount,
  unit,
  onSave,
}: {
  name: string;
  amount: number;
  unit: string | undefined;
  onSave: (amount: number) => Promise<void>;
}) {
  const [text, setText] = useState(String(amount));
  const [seen, setSeen] = useState(amount);
  if (seen !== amount) {
    setSeen(amount);
    setText(String(amount));
  }

  const commit = () => {
    const parsed = parseFoodAmount(text);
    if (parsed === undefined || parsed === amount) {
      setText(String(amount));
      return;
    }
    void onSave(parsed);
  };

  return (
    <label className="food-amount">
      <input
        type="number"
        inputMode="decimal"
        min={0}
        max={MAX_FOOD_AMOUNT}
        step="any"
        aria-label={`Amount of ${name}`}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
            commit();
          }
        }}
      />
      <span>{unitLabel(unitOf({ unit }), Number(text))}</span>
    </label>
  );
}
