import { redirect } from "react-router";

/** Retired: 笔记 is now a filter on 历史. Kept as a redirect for old links. */
export function loader() {
  return redirect("/timeline?kind=note");
}
