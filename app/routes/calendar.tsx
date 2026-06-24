import { redirect } from "react-router";
import type { Route } from "./+types/calendar";

/** Retired: the calendar is now a view of 痕迹. Forwards y/m/date to the new home. */
export function loader({ request }: Route.LoaderArgs) {
  const params = new URLSearchParams(new URL(request.url).search);
  params.set("view", "calendar");
  return redirect(`/timeline?${params.toString()}`);
}
