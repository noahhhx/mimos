import { useId, useState, type ReactNode } from "react";

/**
 * A text field with a list of options under it (an ARIA combobox): typing
 * filters, arrow keys move, Enter or a click chooses, Escape closes. The
 * caller owns the text and the options.
 */
export function Combobox<T>({
  label,
  ariaLabel,
  value,
  placeholder,
  maxLength = 200,
  className,
  options,
  keyOf,
  renderOption,
  onType,
  onChoose,
}: {
  label: string;
  ariaLabel: string;
  value: string;
  placeholder?: string;
  maxLength?: number;
  className?: string;
  options: T[];
  keyOf: (option: T) => string;
  renderOption: (option: T) => ReactNode;
  onType: (value: string) => void;
  onChoose: (option: T) => void;
}) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const showing = open && options.length > 0;

  const choose = (index: number) => {
    const option = options[index];
    if (option === undefined) {
      return;
    }
    setOpen(false);
    onChoose(option);
  };

  return (
    <div className={className ? `combobox ${className}` : "combobox"}>
      <label>
        {label}
        <input
          role="combobox"
          aria-label={ariaLabel}
          aria-expanded={showing}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showing ? `${listId}-${active}` : undefined}
          value={value}
          maxLength={maxLength}
          placeholder={placeholder}
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
              key={keyOf(option)}
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
              {renderOption(option)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
