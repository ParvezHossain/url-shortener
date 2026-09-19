import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { AppShell } from "./AppShell";

const key = `usk_${"a".repeat(24)}_${"b".repeat(64)}`;
const stats = {
  shortCode: "owned",
  shortUrl: "https://links.example/owned",
  originalUrl: "https://example.com/",
  createdAt: "2026-01-01T00:00:00Z",
  expiresAt: null,
  lastAccessedAt: null,
  clickCount: 0,
  customAlias: false,
};
function api() {
  const fetch = vi.fn((url: string, init?: RequestInit) => {
    if (url === "/ui/config")
      return Promise.resolve(
        new Response('{"publicBaseUrl":"https://links.example"}'),
      );
    if (init?.method === "DELETE")
      return Promise.resolve(new Response(null, { status: 204 }));
    return Promise.resolve(
      new Response(JSON.stringify(stats), {
        status: init?.method === "POST" ? 201 : 200,
      }),
    );
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
async function connect() {
  const details = screen.getByText(/API access ·/);
  if (!details.parentElement?.hasAttribute("open"))
    await userEvent.click(details);
  fireEvent.change(screen.getByLabelText("Management API key"), {
    target: { value: key },
  });
  await userEvent.click(screen.getByRole("button", { name: "Use API key" }));
}
afterEach(() => window.history.replaceState(null, "", "/"));

test("V2Management_createAnalyticsDelete_shareMemoryOnlyKey", async () => {
  const fetch = api();
  render(<AppShell />);
  await connect();
  fireEvent.change(screen.getByLabelText("Destination URL"), {
    target: { value: stats.originalUrl },
  });
  await userEvent.click(screen.getByRole("button", { name: "Shorten link" }));
  const analytics = await screen.findByRole("link", { name: "View analytics" });
  expect(analytics).not.toHaveAttribute("target", "_blank");
  const post = fetch.mock.calls.find(([, init]) => init?.method === "POST")!;
  expect(post[0]).toBe("/api/v2/urls");
  expect(new Headers(post[1]?.headers).get("X-API-Key")).toBe(key);
  await act(async () => {
    window.location.hash = "#/analytics?code=owned&mode=v2";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  });
  await screen.findByRole("heading", { name: "Details for owned" });
  await userEvent.click(screen.getByRole("button", { name: "Delete link" }));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "Delete permanently",
    }),
  );
  await screen.findByText(/permanently deleted/);
  const remove = fetch.mock.calls.find(
    ([, init]) => init?.method === "DELETE",
  )!;
  expect(remove[0]).toBe("/api/v2/urls/owned");
  expect(new Headers(remove[1]?.headers).get("X-API-Key")).toBe(key);
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
  expect(document.body.textContent).not.toContain(key);
  expect(window.location.href).not.toContain(key);
});

test("V2Management_clearKey_clearsResultsAndDoesNotFallBack", async () => {
  const fetch = api();
  window.history.replaceState(null, "", "/#/analytics?code=owned&mode=v2");
  render(<AppShell />);
  expect(
    screen.getByRole("heading", { name: "API key required" }),
  ).toBeVisible();
  expect(fetch).not.toHaveBeenCalled();
  await connect();
  await screen.findByRole("heading", { name: "Details for owned" });
  await userEvent.click(screen.getByRole("button", { name: "Clear API key" }));
  expect(screen.queryByText(stats.originalUrl)).not.toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "API key required" }),
  ).toBeVisible();
  expect(fetch.mock.calls.every(([url]) => !url.includes("/api/v1"))).toBe(
    true,
  );
});

test("V2Management_keyChange_abortsCreationAndHidesLateResult", async () => {
  const fetch = api();
  render(<AppShell />);
  await connect();
  let signal: AbortSignal | null | undefined;
  fetch.mockImplementation((url, init) => {
    if (url === "/ui/config")
      return Promise.resolve(
        new Response('{"publicBaseUrl":"https://links.example"}'),
      );
    signal = init?.signal;
    return new Promise(() => {});
  });
  fireEvent.change(screen.getByLabelText("Destination URL"), {
    target: { value: stats.originalUrl },
  });
  await userEvent.click(screen.getByRole("button", { name: "Shorten link" }));
  await waitFor(() => expect(signal).toBeDefined());
  await userEvent.click(screen.getByRole("button", { name: "Clear API key" }));
  expect(signal?.aborted).toBe(true);
  expect(
    screen.queryByText("Your short link is ready."),
  ).not.toBeInTheDocument();
});

test("V2Management_invalidKey_isRejectedLocally", async () => {
  api();
  render(<AppShell />);
  await userEvent.click(screen.getByText(/API access ·/));
  fireEvent.change(screen.getByLabelText("Management API key"), {
    target: { value: "not a key" },
  });
  await userEvent.click(screen.getByRole("button", { name: "Use API key" }));
  expect(screen.getByText(/Enter the complete operator-issued/)).toBeVisible();
  expect(
    screen.queryByRole("link", { name: "My links" }),
  ).not.toBeInTheDocument();
});

test.each([401, 403])(
  "V2Management_rejectedKey_%s_preservesV2WithoutFallback",
  async (status) => {
    const fetch = api();
    window.history.replaceState(null, "", "/#/analytics?code=owned&mode=v2");
    fetch.mockResolvedValue(new Response("private diagnostics", { status }));
    render(<AppShell />);
    await connect();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      status === 401 ? "Your API key was rejected" : "does not have permission",
    );
    expect(fetch.mock.calls.every(([url]) => url.startsWith("/api/v2/"))).toBe(
      true,
    );
    expect(document.body.textContent).not.toContain("private diagnostics");
  },
);

test("V2Management_qr_reusesSharedCredential", async () => {
  const fetch = api();
  window.history.replaceState(null, "", "/#/qr");
  render(<AppShell />);
  await connect();
  expect(screen.queryByLabelText("API key")).not.toBeInTheDocument();
  fetch.mockImplementation((url) =>
    Promise.resolve(
      url.endsWith("/owned")
        ? new Response(JSON.stringify(stats))
        : new Response("image", {
            headers: {
              "Content-Type": url.includes("qr.png")
                ? "image/png"
                : "image/svg+xml",
            },
          }),
    ),
  );
  const createObjectURL = vi.fn().mockReturnValue("blob:preview");
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = createObjectURL;
      static revokeObjectURL = vi.fn();
    },
  );
  fireEvent.change(screen.getByLabelText("Owned short code"), {
    target: { value: "owned" },
  });
  await userEvent.click(
    screen.getByRole("button", { name: "Preview QR code" }),
  );
  await waitFor(() => expect(createObjectURL).toHaveBeenCalledTimes(2));
  expect(fetch.mock.calls).toHaveLength(3);
  for (const [url, init] of fetch.mock.calls) {
    expect(url).toMatch(/^\/api\/v2\/urls\/owned/);
    expect(new Headers(init?.headers).get("X-API-Key")).toBe(key);
  }
});
