import { index, layout, type RouteConfig, route } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  route("api/sync", "routes/api.sync.tsx"),
  layout("routes/app-layout.tsx", [
    index("routes/home.tsx"),
    route("timeline", "routes/timeline.tsx"),
    route("reflections", "routes/reflections.tsx"),
    route("echoes", "routes/echoes.tsx"),
    route("memory", "routes/memory.tsx"),
    route("new", "routes/new.tsx"),
    route("entries/:id", "routes/entry.tsx"),
    route("calendar", "routes/calendar.tsx"),
    route("settings", "routes/settings.tsx"),
    route("upload", "routes/upload.tsx"),
    route("attachments/:id", "routes/attachment.tsx"),
  ]),
] satisfies RouteConfig;
