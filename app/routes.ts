import { index, layout, type RouteConfig, route } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  route("api/sync", "routes/api.sync.tsx"),
  route("api/entry-insight", "routes/api.entry-insight.tsx"),
  route("api/summary", "routes/api.summary.tsx"),
  layout("routes/app-layout.tsx", [
    index("routes/home.tsx"),
    route("timeline", "routes/timeline.tsx"),
    route("notes", "routes/notes.tsx"),
    route("insights", "routes/insights.tsx"),
    route("memory", "routes/memory.tsx"),
    route("new", "routes/new.tsx"),
    route("entries/:id", "routes/entry.tsx"),
    route("calendar", "routes/calendar.tsx"),
    route("settings", "routes/settings.tsx"),
    route("upload", "routes/upload.tsx"),
    route("capture", "routes/capture.tsx"),
    route("download-backup", "routes/download-backup.tsx"),
    route("attachments/:id", "routes/attachment.tsx"),
  ]),
] satisfies RouteConfig;
