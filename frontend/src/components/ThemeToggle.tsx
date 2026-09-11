import { useEffect, useState } from "react";
import { Button } from "./ui";

type Theme = "light" | "dark";
function savedTheme(): Theme | undefined {
  try {
    const value = localStorage.getItem("shortly-theme");
    return value === "light" || value === "dark" ? value : undefined;
  } catch {
    return undefined;
  }
}

/** Follows system appearance until the user explicitly chooses a persistent theme. */
export function ThemeToggle() {
  const [preference, setPreference] = useState(savedTheme);
  const [systemDark, setSystemDark] = useState(
    () => matchMedia("(prefers-color-scheme: dark)").matches,
  );
  const theme = preference ?? (systemDark ? "dark" : "light");
  useEffect(() => {
    const query = matchMedia("(prefers-color-scheme: dark)");
    const update = () => setSystemDark(query.matches);
    query.addEventListener("change", update);
    update();
    return () => query.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  return (
    <Button
      variant="secondary"
      aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
      onClick={() => {
        const next = theme === "dark" ? "light" : "dark";
        setPreference(next);
        try {
          localStorage.setItem("shortly-theme", next);
        } catch {
          /* Still works for this session. */
        }
      }}
    >
      <span aria-hidden="true">{theme === "dark" ? "☀" : "◐"}</span>
      <span>Appearance</span>
    </Button>
  );
}
