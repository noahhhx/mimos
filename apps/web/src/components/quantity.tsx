import { formatQuantity } from "@/lib/format";

/** An ingredient's amount and unit, or nothing for an unmeasured one ("salt, to taste"). */
export function Quantity({ quantity, unit }: { quantity?: number | null; unit?: string | null }) {
  const text = formatQuantity(quantity, unit);
  return text ? <span className="quantity">{text}</span> : null;
}
