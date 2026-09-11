import { render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import { AppShell } from "./AppShell";

test("AppShell_rendersPrimaryNavigationAndFooter", () => {
  render(<AppShell />);
  const navigation = screen.getByRole("navigation", {
    name: "Primary navigation",
  });
  expect(
    within(navigation).getByRole("link", { name: "Overview" }),
  ).toHaveAttribute("href", "#overview");
  expect(
    within(navigation).getByRole("link", { name: /API docs/ }),
  ).toHaveAttribute("href", "/swagger-ui.html");
  expect(screen.getByRole("main")).toHaveAttribute("id", "main");
  expect(screen.getByRole("link", { name: "Skip to content" })).toHaveAttribute(
    "href",
    "#main",
  );
  expect(
    within(screen.getByRole("contentinfo")).getByText(
      "A simpler way to share.",
    ),
  ).toBeVisible();
});
