import { redirect } from "react-router";
import type { Route } from "./+types/new";

/** Retired: writing now lives on 此刻. Forwards any ?kind=/?date= to the composer. */
export function loader({ request }: Route.LoaderArgs) {
  return redirect(`/${new URL(request.url).search}`);
}
