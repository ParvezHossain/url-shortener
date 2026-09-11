import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { ApiErrorBoundary } from "./ApiErrorBoundary";

test("ApiErrorBoundary_unexpectedFailure_showsSafeRecoveryUi", () => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  function Broken(): never {
    throw new Error("private stack trace");
  }
  render(
    <ApiErrorBoundary>
      <Broken />
    </ApiErrorBoundary>,
  );
  expect(screen.getByRole("heading")).toHaveTextContent(
    "Something didn’t load correctly",
  );
  expect(screen.getByRole("link", { name: "Start again" })).toHaveAttribute(
    "href",
    "/",
  );
  expect(screen.queryByText(/private stack trace/)).not.toBeInTheDocument();
});
test("ApiErrorBoundary_healthyChild_rendersApplication", () => {
  render(
    <ApiErrorBoundary>
      <p>Application content</p>
    </ApiErrorBoundary>,
  );
  expect(screen.getByText("Application content")).toBeVisible();
});
