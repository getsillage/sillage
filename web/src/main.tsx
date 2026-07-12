import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { App } from "./app/App";
import { I18nProvider } from "./i18n/I18nProvider";
import "./styles/app.css";

const root = document.getElementById("root");
const router = createBrowserRouter([{ path: "*", element: <App /> }]);

if (root) {
  createRoot(root).render(
    <StrictMode>
      <I18nProvider>
        <RouterProvider router={router} />
      </I18nProvider>
    </StrictMode>,
  );
}
