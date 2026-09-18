import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { QrCodePanel } from "./QrCodePanel";

beforeEach(() => {
  let count = 0;
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => `blob:qr-${++count}`),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: vi.fn(),
  });
});
function success() {
  return vi.fn(async (url: string) => {
    if (url.includes("qr.png"))
      return new Response(new Uint8Array([137, 80, 78, 71]), {
        headers: { "Content-Type": "image/png" },
      });
    if (url.includes("qr.svg"))
      return new Response('<svg xmlns="http://www.w3.org/2000/svg"/>', {
        headers: { "Content-Type": "image/svg+xml" },
      });
    return new Response(
      JSON.stringify({ shortCode: "owned", shortUrl: "https://sho.rt/owned" }),
    );
  });
}
function fill() {
  fireEvent.change(screen.getByLabelText("Owned short code"), {
    target: { value: "owned" },
  });
  fireEvent.change(screen.getByLabelText("API key"), {
    target: { value: "private-key" },
  });
}

test("QrCodePanel_ownedLink_previewsAndDownloadsBothFormatsWithoutPersistingKey", async () => {
  vi.stubGlobal("fetch", success());
  const user = userEvent.setup();
  const { unmount } = render(<QrCodePanel />);
  fill();
  await user.click(screen.getByRole("button", { name: "Preview QR code" }));
  expect(
    await screen.findByRole("img", {
      name: "QR code for https://sho.rt/owned",
    }),
  ).toHaveAttribute("src", "blob:qr-1");
  expect(screen.getByRole("link", { name: "Download PNG" })).toHaveAttribute(
    "download",
    "owned-qr.png",
  );
  expect(screen.getByRole("link", { name: "Download SVG" })).toHaveAttribute(
    "href",
    "blob:qr-2",
  );
  expect(
    screen.getByRole("link", { name: "https://sho.rt/owned" }),
  ).toHaveAttribute("href", "https://sho.rt/owned");
  for (const [url, init] of vi.mocked(fetch).mock.calls) {
    expect(String(url)).not.toContain("private-key");
    expect(init?.headers).toEqual({ "X-API-Key": "private-key" });
  }
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
  unmount();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:qr-1");
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:qr-2");
});

test.each([
  [401, "Enter a valid API key"],
  [404, "No owned link found"],
  [429, "Too many requests"],
  [500, "temporarily unavailable"],
])(
  "QrCodePanel_status%s_showsSafeFeedbackAndNoDownload",
  async (status, message) => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("private server detail", { status: Number(status) }),
        ),
    );
    render(<QrCodePanel />);
    fill();
    fireEvent.submit(screen.getByRole("form", { name: "Generate QR code" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(String(message));
    expect(
      screen.queryByRole("link", { name: "Download PNG" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("private server detail")).not.toBeInTheDocument();
  },
);

test("QrCodePanel_invalidOptions_preventsRequest", async () => {
  const fetcher = vi.fn();
  vi.stubGlobal("fetch", fetcher);
  render(<QrCodePanel />);
  fill();
  fireEvent.change(screen.getByLabelText("Image size (pixels)"), {
    target: { value: "4096" },
  });
  fireEvent.submit(screen.getByRole("form", { name: "Generate QR code" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("128 to 1024");
  expect(fetcher).not.toHaveBeenCalled();
});

test("QrCodePanel_clear_revokesPreviewAndClearsCredential", async () => {
  vi.stubGlobal("fetch", success());
  render(<QrCodePanel />);
  fill();
  fireEvent.submit(screen.getByRole("form", { name: "Generate QR code" }));
  await screen.findByRole("img");
  fireEvent.click(
    screen.getByRole("button", { name: "Clear API key and preview" }),
  );
  expect(screen.getByLabelText("API key")).toHaveValue("");
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
});

test("QrCodePanel_unmount_abortsPendingRequestAndPreventsDuplicateSubmission", async () => {
  const fetcher = vi.fn(() => new Promise<Response>(() => {}));
  vi.stubGlobal("fetch", fetcher);
  const { unmount } = render(<QrCodePanel />);
  fill();
  const form = screen.getByRole("form", { name: "Generate QR code" });
  fireEvent.submit(form);
  fireEvent.submit(form);
  await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
  const signal = vi.mocked(fetch).mock.calls[0][1]?.signal;
  unmount();
  expect(signal?.aborted).toBe(true);
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});
