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

function normalizeOptions(options: readonly string[]): string[] {
  return Array.from(new Set(options.map((option) => option.trim()).filter(Boolean)));
}

function splitDelimitedValues(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function nextValue(current: string, selected: string, mode: SelectionMode): string {
  if (mode === "replace") {
    return selected;
  }

  const values = splitDelimitedValues(current);
  if (!values.includes(selected)) {
    values.push(selected);
  }
  return values.join(", ");
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
  const [open, setOpen] = useState(false);
  const listboxId = `${useId().replaceAll(":", "")}-options`;
  const normalizedOptions = normalizeOptions(options);
  const hasOptions = normalizedOptions.length > 0;
  const isControlled = value !== undefined;

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

  function commitOption(option: string) {
    const input = inputRef.current;
    const current = value ?? input?.value ?? "";
    const updated = nextValue(current, option, selectionMode);
    if (input && !isControlled) {
      input.value = updated;
    }
    onValueChange?.(updated);
    setOpen(false);
    requestAnimationFrame(() => input?.focus());
  }

  return (
    <div ref={rootRef} className="relative">
      <input
        id={id}
        ref={inputRef}
        type={type}
        name={name}
        role="combobox"
        value={value}
        defaultValue={isControlled ? undefined : defaultValue}
        onChange={(event) => onValueChange?.(event.target.value)}
        onBlur={(event) => {
          if (!rootRef.current?.contains(event.relatedTarget as Node | null)) {
            setOpen(false);
          }
        }}
        onFocus={() => {
          if (hasOptions) {
            setOpen(true);
          }
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && hasOptions) {
            setOpen(true);
          }
          if (event.key === "Escape") {
            setOpen(false);
          }
        }}
        placeholder={placeholder}
        autoComplete={autoComplete}
        disabled={disabled}
        aria-autocomplete="list"
        aria-controls={hasOptions ? listboxId : undefined}
        aria-expanded={hasOptions ? open : undefined}
        aria-haspopup={hasOptions ? "listbox" : undefined}
        className={`${inputClassName} ${hasOptions ? "pr-10" : ""}`}
      />

      {hasOptions ? (
        <button
          type="button"
          aria-label={optionLabel}
          aria-expanded={open}
          aria-controls={listboxId}
          className="absolute top-6 right-2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-full text-gray-400 transition hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-gray-900/10"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            setOpen((value) => !value);
            inputRef.current?.focus();
          }}
        >
          <span
            aria-hidden="true"
            className={`block h-1.5 w-1.5 border-gray-400 border-r border-b transition-transform ${
              open ? "rotate-[225deg]" : "rotate-45"
            }`}
          />
        </button>
      ) : null}

      {open && hasOptions ? (
        <div
          id={listboxId}
          role="listbox"
          className="absolute right-0 left-0 z-40 mt-1 max-h-52 overflow-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg shadow-gray-900/10"
        >
          {normalizedOptions.map((option) => (
            <button
              key={option}
              type="button"
              role="option"
              aria-selected="false"
              className="block w-full px-3 py-2 text-left text-gray-700 text-sm transition hover:bg-gray-50 hover:text-gray-950 focus:bg-gray-50 focus:outline-none"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => commitOption(option)}
            >
              {option}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
