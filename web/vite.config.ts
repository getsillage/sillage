import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": "http://localhost:5231",
      "/file": "http://localhost:5231",
      "/sillage.api.v1": "http://localhost:5231",
    },
  },
  build: {
    outDir: "../server/router/frontend/dist",
    emptyOutDir: true,
  },
});
