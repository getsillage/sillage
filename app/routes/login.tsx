import { env } from "cloudflare:workers";
import { Form, redirect, useNavigation } from "react-router";
import { verifyPassword } from "~/lib/auth/password";
import { clearLoginAttempts, isLoginRateLimited, recordFailedLogin } from "~/lib/auth/rate-limit";
import { safeRedirect } from "~/lib/auth/redirect";
import { createUserSession, isAuthenticated } from "~/lib/auth/session";
import { loginSchema } from "~/lib/validation/auth";
import type { Route } from "./+types/login";

export function meta(_: Route.MetaArgs) {
  return [{ title: "登录 · 我的日记" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  if (await isAuthenticated(request, env)) {
    throw redirect("/");
  }
  return null;
}

export async function action({ request }: Route.ActionArgs) {
  if (await isLoginRateLimited(env, request)) {
    return { error: "登录尝试次数过多，请稍后再试" };
  }

  const form = await request.formData();
  const parsed = loginSchema.safeParse({
    password: form.get("password"),
    redirectTo: form.get("redirectTo") ?? undefined,
  });
  if (!parsed.success) {
    return { error: "请输入密码" };
  }

  const valid = await verifyPassword(parsed.data.password, env.APP_PASSWORD_HASH);
  if (!valid) {
    await recordFailedLogin(env, request);
    return { error: "密码错误" };
  }

  await clearLoginAttempts(env, request);
  return createUserSession(env, safeRedirect(parsed.data.redirectTo));
}

export default function Login({ actionData }: Route.ComponentProps) {
  const navigation = useNavigation();
  const submitting = navigation.state === "submitting";

  return (
    <main className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
      <Form
        method="post"
        className="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm"
      >
        <h1 className="text-xl font-semibold text-gray-900">我的日记</h1>
        <p className="mt-1 text-sm text-gray-500">输入密码以继续</p>

        <label className="mt-6 block text-sm font-medium text-gray-700">
          密码
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-gray-900 focus:outline-none"
          />
        </label>

        {actionData?.error ? <p className="mt-3 text-sm text-red-600">{actionData.error}</p> : null}

        <button
          type="submit"
          disabled={submitting}
          className="mt-6 w-full rounded-lg bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-60"
        >
          {submitting ? "登录中…" : "登录"}
        </button>
      </Form>
    </main>
  );
}
