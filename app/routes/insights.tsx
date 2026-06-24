import { redirect } from "react-router";

/** Retired: 洞察 merged into 微光's 照见 tab. Kept as a redirect for old links. */
export function loader() {
  return redirect("/memory?tab=review");
}
