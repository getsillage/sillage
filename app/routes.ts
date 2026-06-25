import { index, layout, type RouteConfig, route } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  route("api/sync", "routes/api.sync.tsx"),
  route("api/entry-insight", "routes/api.entry-insight.tsx"),
  route("api/summary", "routes/api.summary.tsx"),
  route("api/ask-stream", "routes/api.ask-stream.tsx"),
  route("api/ask-stop", "routes/api.ask-stop.tsx"),
  layout("routes/app-layout.tsx", [
    index("routes/home.tsx"),
    route("timeline", "routes/timeline.tsx"),
    route("review", "routes/review.tsx"),
    route("ask", "routes/ask.tsx"),
    route("notes", "routes/notes.tsx"),
    route("new", "routes/new.tsx"),
    route("entries/:id", "routes/entry.tsx"),
    route("calendar", "routes/calendar.tsx"),
    route("settings", "routes/settings.tsx"),
    route("upload", "routes/upload.tsx"),
    route("capture", "routes/capture.tsx"),
    route("download-backup", "routes/download-backup.tsx"),
    route("download-ask-conversation", "routes/download-ask-conversation.tsx"),
    route("attachments/:id", "routes/attachment.tsx"),
  ]),
] satisfies RouteConfig;
