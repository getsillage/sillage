import { index, layout, type RouteConfig, route } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  layout("routes/app-layout.tsx", [
    index("routes/home.tsx"),
    route("new", "routes/new.tsx"),
    route("entries/:id", "routes/entry.tsx"),
    route("calendar", "routes/calendar.tsx"),
    route("search", "routes/search.tsx"),
    route("upload", "routes/upload.tsx"),
    route("attachments/:id", "routes/attachment.tsx"),
  ]),
] satisfies RouteConfig;
