import { createRequestHandler, RouterContextProvider } from "react-router";
import { waitUntilContext } from "~/lib/request-context";

const requestHandler = createRequestHandler(
  () => import("virtual:react-router/server-build"),
  import.meta.env.MODE,
);

export default {
  async fetch(request, _env, ctx) {
    const context = new RouterContextProvider();
    context.set(waitUntilContext, (promise) => ctx.waitUntil(promise));
    return requestHandler(request, context);
  },
} satisfies ExportedHandler<Env>;
