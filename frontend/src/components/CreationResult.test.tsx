import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { CreationResult, type CreatedLink } from "./CreationResult";

const result: CreatedLink = {
  shortCode: "abc",
  shortUrl: "https://links.example.test/abc",
  originalUrl: "https://example.com/a/long/path",
  createdAt: "2026-09-11T00:00:00Z",
  expiresAt: null,
  customAlias: false,
};
function mount(value = result) {
  render(<CreationResult result={value} onReset={vi.fn()} />);
}

test("CreationResult_success_displaysShortUrlAndMetadata", async () => {
  mount();
  expect(screen.getByRole("heading")).toHaveFocus();
  await waitFor(() =>
    expect(screen.getByRole("status")).toHaveTextContent(
      "Your short link is ready.",
    ),
  );
  expect(screen.getByText(result.originalUrl)).toBeVisible();
  expect(screen.getByText("Never expires")).toBeVisible();
  expect(screen.getByText("Generated code")).toBeVisible();
  expect(screen.getByRole("link", { name: /Open link/ })).toHaveAttribute(
    "href",
    result.shortUrl,
  );
  expect(screen.getByRole("link", { name: /View analytics/ })).toHaveAttribute(
    "href",
    "/#/analytics?code=abc",
  );
});

test("CreationResult_customAliasAndExpiry_displaysConfirmedMetadata", () => {
  mount({ ...result, customAlias: true, expiresAt: "2099-10-20T12:00:00Z" });
  expect(screen.getByText("Custom alias")).toBeVisible();
  expect(screen.getByTitle("2099-10-20T12:00:00Z")).toHaveAttribute(
    "datetime",
    "2099-10-20T12:00:00Z",
  );
});

test("CreationResult_copySupported_copiesShortUrlAndConfirms", async () => {
  const user = userEvent.setup();
  const write = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
  mount();
  await user.click(screen.getByRole("button", { name: "Copy link" }));
  expect(write).toHaveBeenCalledWith(result.shortUrl);
  expect(screen.getByRole("status")).toHaveTextContent(
    "Short link copied to clipboard.",
  );
});

test.each(["missing", "denied"])(
  "CreationResult_clipboardUnavailable_showsManualCopyFallback (%s)",
  async (kind) => {
    const user = userEvent.setup();
    if (kind === "missing")
      vi.spyOn(navigator, "clipboard", "get").mockReturnValue(
        undefined as unknown as Clipboard,
      );
    else
      vi.spyOn(navigator.clipboard, "writeText").mockRejectedValue(
        new DOMException("Denied", "NotAllowedError"),
      );
    mount();
    await user.click(screen.getByRole("button", { name: "Copy link" }));
    const input = screen.getByRole("textbox", {
      name: "Copy short link manually",
    }) as HTMLInputElement;
    expect(input).toHaveValue(result.shortUrl);
    expect(input).toHaveFocus();
    expect(input.selectionStart).toBe(0);
    expect(input.selectionEnd).toBe(result.shortUrl.length);
    expect(screen.getByRole("status")).toHaveTextContent("copy command");
  },
);

test("CreationResult_shareUnsupported_hidesAction", () => {
  mount();
  expect(
    screen.queryByRole("button", { name: "Share link" }),
  ).not.toBeInTheDocument();
});

test("CreationResult_shareSupported_sendsOnlyShortLink", async () => {
  const share = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { share });
  mount();
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Share link" }));
  expect(share).toHaveBeenCalledWith({
    title: "Short link",
    url: result.shortUrl,
  });
  expect(screen.getByRole("status")).toHaveTextContent("Link shared.");
});

test.each(["AbortError", "NotAllowedError"])(
  "CreationResult_shareFailure_keepsResultAvailable (%s)",
  async (name) => {
    vi.stubGlobal("navigator", {
      share: vi.fn().mockRejectedValue(new DOMException("Failed", name)),
    });
    mount();
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Share link" }));
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent(
        name === "AbortError" ? "Sharing cancelled" : "copy the link instead",
      ),
    );
    expect(screen.getByRole("link", { name: result.shortUrl })).toBeVisible();
  },
);
