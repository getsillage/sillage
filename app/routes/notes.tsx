import { redirect } from "react-router";

/** Retired: all writing now lives on 记录. Kept as a redirect for old links. */
export function loader() {
  return redirect("/timeline");
}
