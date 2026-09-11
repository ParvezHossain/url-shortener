import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ApiErrorBoundary } from "./components/ApiErrorBoundary";
import { AppShell } from "./components/AppShell";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ApiErrorBoundary>
      <AppShell />
    </ApiErrorBoundary>
  </StrictMode>,
);
