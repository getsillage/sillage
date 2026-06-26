import { Form, useNavigation } from "react-router";
import { todayISO } from "~/lib/date";
import type { EntryKind, NoteType } from "~/lib/product/entry-fields";
import { MarkdownEditor } from "./MarkdownEditor";
import { inputClass, primaryButtonClass } from "./ui";

export interface EntryFormDefaults {
  entryDate: string;
  body: string;
  kind: EntryKind;
  noteType: NoteType | null;
}

interface EntryFormProps {
  defaults?: EntryFormDefaults;
  error?: string | null;
  submitLabel?: string;
  intent?: string;
}

export function EntryForm({ defaults, error, submitLabel = "保存", intent }: EntryFormProps) {
  const navigation = useNavigation();
  const busy = navigation.state !== "idle";

  return (
    <Form method="post" className="space-y-4">
      {intent ? <input type="hidden" name="intent" value={intent} /> : null}
      <input type="hidden" name="kind" value={defaults?.kind ?? "fragment"} />
      <input type="hidden" name="noteType" value={defaults?.noteType ?? ""} />

      <label className="block text-sm text-gray-500 dark:text-gray-400">
        日期
        <input
          type="date"
          name="entryDate"
          required
          defaultValue={defaults?.entryDate ?? todayISO()}
          className={`${inputClass} max-w-44`}
        />
      </label>

      <MarkdownEditor
        name="body"
        defaultValue={defaults?.body ?? ""}
        placeholder="写下此刻想留下的内容…"
      />

      {error ? <p className="text-red-600 text-sm dark:text-red-400">{error}</p> : null}

      <div className="flex justify-end">
        <button type="submit" disabled={busy} className={`${primaryButtonClass} w-full sm:w-auto`}>
          {busy ? "保存中…" : submitLabel}
        </button>
      </div>
    </Form>
  );
}
