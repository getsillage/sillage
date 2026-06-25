import { useEffect, useId, useRef, useState } from "react";
import { inputClass } from "./ui";

type SelectionMode = "replace" | "append";

interface SuggestedInputProps {
  id: string;
  name: string;
  optionLabel: string;
  options: readonly string[];
  autoComplete?: string;
  defaultValue?: string;
  disabled?: boolean;
  inputClassName?: string;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  selectionMode?: SelectionMode;
  type?: "text" | "url" | "password";
  value?: string;
}

const DELIMITER = /[,，\s]+/;

function normalizeOptions(options: readonly string[]): string[] {
  return Array.from(new Set(options.map((option) => option.trim()).filter(Boolean)));
}

function splitDelimitedValues(raw: string): string[] {
  return raw
    .split(DELIMITER)
    .map((item) => item.trim())
    .filter(Boolean);
}

/**
 * In append mode the field holds a delimited list. The text after the last
 * delimiter is the token currently being typed (the live query); everything
 * before it is already committed.
 */
function parseAppendState(raw: string): { committed: string[]; active: string } {
  const activeMatch = raw.match(/[^,，\s]*$/);
  const active = activeMatch ? activeMatch[0] : "";
  const committedPart = active ? raw.slice(0, raw.length - active.length) : raw;
  return { committed: splitDelimitedValues(committedPart), active };
}

function appendOption(current: string, selected: string): string {
  const { committed } = parseAppendState(current);
  const seen = new Set(committed.map((value) => value.toLowerCase()));
  const next = seen.has(selected.toLowerCase()) ? committed : [...committed, selected];
  // Trailing separator lets the user start typing the next value immediately.
  return next.length > 0 ? `${next.join(", ")}, ` : "";
}

function Highlight({ text, query }: { text: string; query: string }) {
  if (!query) {
    return <>{text}</>;
  }
  const index = text.toLowerCase().indexOf(query.toLowerCase());
  if (index === -1) {
    return <>{text}</>;
  }
  return (
    <>
      {text.slice(0, index)}
      <span className="font-semibold text-gray-950 dark:text-gray-50">
        {text.slice(index, index + query.length)}
      </span>
      {text.slice(index + query.length)}
    </>
  );
}

export function SuggestedInput({
  id,
  name,
  optionLabel,
  options,
  autoComplete,
  defaultValue,
  disabled,
  inputClassName = inputClass,
  onValueChange,
  placeholder,
  selectionMode = "replace",
  type = "text",
  value,
}: SuggestedInputProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const activeOptionRef = useRef<HTMLButtonElement | null>(null);
  const isControlled = value !== undefined;

  const [text, setText] = useState(value ?? defaultValue ?? "");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const listboxId = `${useId().replaceAll(":", "")}-options`;
  const normalizedOptions = normalizeOptions(options);
  const hasOptions = normalizedOptions.length > 0;

  // Controlled callers own the value; uncontrolled ones use local state.
  const currentText = isControlled ? (value ?? "") : text;

  const { committed, active } =
    selectionMode === "append"
      ? parseAppendState(currentText)
      : { committed: [] as string[], active: currentText.trim() };
  const query = active.trim();
  const committedSet = new Set(committed.map((entry) => entry.toLowerCase()));
  const available = normalizedOptions.filter((option) => !committedSet.has(option.toLowerCase()));
  const normalizedQuery = query.toLowerCase();
  const isExactMatch = available.some((option) => option.toLowerCase() === normalizedQuery);
  // Show every option when the query exactly matches one (lets the user browse
  // or switch), otherwise narrow to substring matches.
  const filtered =
    normalizedQuery && !isExactMatch
      ? available.filter((option) => option.toLowerCase().includes(normalizedQuery))
      : available;
  const safeIndex = filtered.length > 0 ? Math.min(activeIndex, filtered.length - 1) : -1;

  useEffect(() => {
    if (!hasOptions) {
      setOpen(false);
    }
  }, [hasOptions]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function onPointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  useEffect(() => {
    if (open && safeIndex >= 0) {
      activeOptionRef.current?.scrollIntoView({ block: "nearest" });
    }
  }, [open, safeIndex]);

  function updateText(next: string) {
    if (!isControlled) {
      setText(next);
    }
    onValueChange?.(next);
  }

  function openList() {
    if (hasOptions) {
      setOpen(true);
      setActiveIndex(0);
    }
  }

  function commitOption(option: string) {
    const updated = selectionMode === "append" ? appendOption(currentText, option) : option;
    updateText(updated);
    if (selectionMode === "append") {
      setOpen(true);
      setActiveIndex(0);
    } else {
      setOpen(false);
    }
    requestAnimationFrame(() => {
      const input = inputRef.current;
      if (input) {
        input.focus();
        const caret = input.value.length;
        input.setSelectionRange?.(caret, caret);
      }
    });
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!hasOptions) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      if (!open) {
        openList();
        return;
      }
      setActiveIndex((index) => (filtered.length > 0 ? (index + 1) % filtered.length : 0));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      if (!open) {
        openList();
        return;
      }
      setActiveIndex((index) =>
        filtered.length > 0 ? (index - 1 + filtered.length) % filtered.length : 0,
      );
    } else if (event.key === "Enter") {
      if (open && safeIndex >= 0) {
        event.preventDefault();
        commitOption(filtered[safeIndex]);
      }
    } else if (event.key === "Escape") {
      if (open) {
        event.preventDefault();
        setOpen(false);
      }
    }
  }

  const activeOptionId = open && safeIndex >= 0 ? `${listboxId}-opt-${safeIndex}` : undefined;

  return (
    <div ref={rootRef} className="relative">
      <input
        id={id}
        ref={inputRef}
        type={type}
        name={name}
        role="combobox"
        value={currentText}
        onChange={(event) => {
          updateText(event.target.value);
          setActiveIndex(0);
          if (hasOptions) {
            setOpen(true);
          }
        }}
        onBlur={(event) => {
          if (!rootRef.current?.contains(event.relatedTarget as Node | null)) {
            setOpen(false);
          }
        }}
        onFocus={openList}
        onKeyDown={onKeyDown}
        placeholder={placeholder}
        autoComplete={autoComplete}
        disabled={disabled}
        aria-autocomplete="list"
        aria-controls={hasOptions ? listboxId : undefined}
        aria-expanded={hasOptions ? open : undefined}
        aria-haspopup={hasOptions ? "listbox" : undefined}
        aria-activedescendant={activeOptionId}
        className={`${inputClassName} ${hasOptions ? "pr-10" : ""}`}
      />

      {hasOptions ? (
        <button
          type="button"
          aria-label={optionLabel}
          aria-expanded={open}
          aria-controls={listboxId}
          tabIndex={-1}
          className="absolute top-6 right-2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-full text-gray-400 transition hover:bg-celadon-50 hover:text-celadon-700 focus:outline-none focus:ring-2 focus:ring-celadon-600/20 dark:text-gray-500 dark:hover:bg-celadon-900/40 dark:hover:text-celadon-200 dark:focus:ring-celadon-400/30"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            setActiveIndex(0);
            setOpen((current) => !current);
            inputRef.current?.focus();
          }}
        >
          <span
            aria-hidden="true"
            className={`block h-1.5 w-1.5 border-gray-400 border-r border-b transition-transform dark:border-gray-500 ${
              open ? "rotate-[225deg]" : "rotate-45"
            }`}
          />
        </button>
      ) : null}

      {open && hasOptions ? (
        <div
          id={listboxId}
          role="listbox"
          className="absolute right-0 left-0 z-40 mt-1 max-h-52 overflow-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30"
        >
          {filtered.length === 0 ? (
            <p className="px-3 py-2 text-gray-400 text-sm dark:text-gray-500">无匹配项</p>
          ) : (
            filtered.map((option, index) => {
              const isActive = index === safeIndex;
              return (
                <button
                  key={option}
                  id={`${listboxId}-opt-${index}`}
                  ref={isActive ? activeOptionRef : undefined}
                  type="button"
                  role="option"
                  aria-selected={isActive}
                  className={`block w-full px-3 py-2 text-left text-sm transition focus:outline-none ${
                    isActive
                      ? "bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200"
                      : "text-gray-700 hover:bg-gray-50 hover:text-gray-950 dark:text-gray-200 dark:hover:bg-gray-800 dark:hover:text-gray-50"
                  }`}
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => commitOption(option)}
                >
                  <Highlight text={option} query={query} />
                </button>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}
