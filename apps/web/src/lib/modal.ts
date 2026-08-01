export function hasVisibleModal(excludedModal?: HTMLElement | null): boolean {
  return Array.from(
    document.querySelectorAll<HTMLElement>('[aria-modal="true"]'),
  ).some(
    (modal) =>
      modal !== excludedModal &&
      !modal.hidden &&
      modal.getAttribute("aria-hidden") !== "true",
  );
}
