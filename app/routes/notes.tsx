import { redirect } from "react-router";

/** Retired: 笔记 is now a filter on 痕迹. Kept as a redirect for old links. */
export function loader() {
  return redirect("/timeline?kind=note");
}
