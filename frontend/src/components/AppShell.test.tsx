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
  ).toHaveAttribute("href", "/#overview");
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

test("AppShell_analyticsRoute_rendersLookupInsteadOfCreation", () => {
  window.history.replaceState(null, "", "/#/analytics");
  try {
    render(<AppShell />);
    expect(
      screen.getByRole("heading", { name: "Link analytics" }),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "Analytics" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.queryByLabelText("Destination URL")).not.toBeInTheDocument();
  } finally {
    window.history.replaceState(null, "", "/");
  }
});

test("AppShell_qrRoute_rendersOwnedQrActions", () => {
  window.history.replaceState(null, "", "/#/qr");
  try {
    render(<AppShell />);
    expect(screen.getByRole("heading", { name: "QR codes" })).toBeVisible();
    expect(screen.getByRole("link", { name: "QR codes" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByLabelText("API key")).toHaveAttribute(
      "type",
      "password",
    );
  } finally {
    window.history.replaceState(null, "", "/");
  }
});
