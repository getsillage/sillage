import { env } from "cloudflare:workers";
import { Form, redirect, useNavigation } from "react-router";
import { inputClass, pageLeadClass, pageTitleClass, primaryButtonClass } from "~/components/ui";
import { verifyPassword } from "~/lib/auth/password";
import { clearLoginAttempts, isLoginRateLimited, recordFailedLogin } from "~/lib/auth/rate-limit";
import { safeRedirect } from "~/lib/auth/redirect";
import { createUserSession, isAuthenticated } from "~/lib/auth/session";
import { loginSchema } from "~/lib/validation/auth";
import type { Route } from "./+types/login";

export function meta(_: Route.MetaArgs) {
  return [{ title: "登录 · Sillage" }];
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
    <main className="flex min-h-screen items-center justify-center bg-gray-50 px-4 py-10 dark:bg-gray-950">
      <Form
        method="post"
        className="w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-800 dark:bg-gray-900"
      >
        <h1 className={pageTitleClass}>Sillage</h1>
        <p className={pageLeadClass}>输入密码以继续。</p>

        <label className="mt-6 block font-medium text-gray-900 text-sm dark:text-gray-100">
          密码
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            className={inputClass}
          />
        </label>

        {actionData?.error ? (
          <p className="mt-3 text-red-600 text-sm dark:text-red-400">{actionData.error}</p>
        ) : null}

        <button type="submit" disabled={submitting} className={`${primaryButtonClass} mt-6 w-full`}>
          {submitting ? "登录中…" : "登录"}
        </button>
      </Form>
    </main>
  );
}
