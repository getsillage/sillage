/**
 * Returns `to` only if it is a safe internal path, otherwise `fallback`.
 * Prevents open-redirect via attacker-controlled `redirectTo` values.
 */
export function safeRedirect(
  to: FormDataEntryValue | string | null | undefined,
  fallback = "/",
): string {
  if (!to || typeof to !== "string") {
    return fallback;
  }
  if (!to.startsWith("/") || to.startsWith("//")) {
    return fallback;
  }
  return to;
}
