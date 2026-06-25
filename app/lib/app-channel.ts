export type AppChannel = "development" | "beta" | "production";

export type AppVersionBadge = {
  label: string;
  tone: "development" | "beta";
};

type RuntimeOptions = {
  isDevelopment?: boolean;
};

function isDevelopmentRuntime(options: RuntimeOptions = {}): boolean {
  return options.isDevelopment ?? import.meta.env.DEV;
}

function configuredChannel(env: Env): Exclude<AppChannel, "development"> {
  return env.APP_RELEASE_CHANNEL === "beta" ? "beta" : "production";
}

export function getAppChannel(env: Env, options: RuntimeOptions = {}): AppChannel {
  if (isDevelopmentRuntime(options)) {
    return "development";
  }
  return configuredChannel(env);
}

export function getAppVersionBadge(env: Env, options: RuntimeOptions = {}): AppVersionBadge | null {
  const channel = getAppChannel(env, options);
  if (channel === "development") {
    return { label: "开发版", tone: "development" };
  }
  if (channel === "beta") {
    return { label: "β版", tone: "beta" };
  }
  return null;
}

export function shouldBypassAuth(env: Env, options: RuntimeOptions = {}): boolean {
  return !isDevelopmentRuntime(options) && configuredChannel(env) === "beta";
}
