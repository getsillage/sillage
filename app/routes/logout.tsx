import { env } from "cloudflare:workers";
import { redirect } from "react-router";
import { logout } from "~/lib/auth/session";
import type { Route } from "./+types/logout";

export async function action({ request }: Route.ActionArgs) {
  return logout(request, env);
}

// Visiting /logout directly (GET) just bounces home; logout happens via POST.
export async function loader() {
  throw redirect("/");
}
