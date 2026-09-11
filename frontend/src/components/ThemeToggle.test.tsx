import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ThemeToggle } from "./ThemeToggle";

test("ThemeToggle_userChangesTheme_persistsPreference", async () => {
  const user = userEvent.setup();
  const view = render(<ThemeToggle />);
  await user.click(
    screen.getByRole("button", { name: "Switch to dark theme" }),
  );
  expect(localStorage.getItem("shortly-theme")).toBe("dark");
  expect(document.documentElement.dataset.theme).toBe("dark");
  view.unmount();
  render(<ThemeToggle />);
  expect(
    screen.getByRole("button", { name: "Switch to light theme" }),
  ).toBeVisible();
});

test("ThemeToggle_systemChanges_followsUntilExplicitChoice", async () => {
  const user = userEvent.setup();
  let change = () => {};
  const query = {
    matches: true,
    addEventListener: vi.fn((_event, callback) => {
      change = callback;
    }),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal("matchMedia", () => query);
  render(<ThemeToggle />);
  expect(document.documentElement.dataset.theme).toBe("dark");
  query.matches = false;
  act(() => change());
  expect(document.documentElement.dataset.theme).toBe("light");
  await user.click(screen.getByRole("button"));
  act(() => change());
  expect(document.documentElement.dataset.theme).toBe("dark");
});

test("ThemeToggle_storageUnavailable_stillChangesTheme", async () => {
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
    throw new Error("Unavailable");
  });
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
    throw new Error("Unavailable");
  });
  render(<ThemeToggle />);
  await userEvent.setup().click(screen.getByRole("button"));
  expect(document.documentElement.dataset.theme).toBe("dark");
});
