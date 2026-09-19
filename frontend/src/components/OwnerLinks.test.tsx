import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { OwnerLinks } from "./OwnerLinks";

const link = {
  shortCode: "owned",
  originalUrl: "https://example.com",
  clickCount: 2,
};
test("OwnerLinks_pagesOwnedResultsAndLinksToAuthenticatedAnalytics", async () => {
  const fetch = vi.fn().mockImplementation((url: string) =>
    Promise.resolve(
      new Response(
        JSON.stringify({
          content: [link],
          page: url.includes("page=1") ? 1 : 0,
          size: 20,
          totalElements: 21,
        }),
      ),
    ),
  );
  vi.stubGlobal("fetch", fetch);
  render(<OwnerLinks apiKey="secret" />);
  expect(await screen.findByRole("link", { name: "owned" })).toHaveAttribute(
    "href",
    "/#/analytics?code=owned&mode=v2",
  );
  expect(fetch.mock.calls[0][0]).toBe("/api/v2/urls?page=0&size=20");
  await userEvent.click(screen.getByRole("button", { name: "Next page" }));
  await screen.findByText("21 links · Page 2");
  expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
  expect(fetch.mock.calls[1][0]).toBe("/api/v2/urls?page=1&size=20");
});

test("OwnerLinks_malformedResponse_showsSafeErrorAndAllowsRefresh", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(new Response('{"content":[null]}'))
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0 }),
      ),
    );
  vi.stubGlobal("fetch", fetch);
  render(<OwnerLinks apiKey="secret" />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "unexpected response",
  );
  await userEvent.click(screen.getByRole("button", { name: "Refresh links" }));
  await screen.findByText(/No links on this page/);
});

test("OwnerLinks_unmount_abortsPendingLookup", async () => {
  let signal: AbortSignal | undefined;
  vi.stubGlobal(
    "fetch",
    vi.fn((_url, init) => {
      signal = init.signal;
      return new Promise(() => {});
    }),
  );
  const view = render(<OwnerLinks apiKey="secret" />);
  await waitFor(() => expect(signal).toBeDefined());
  view.unmount();
  expect(signal?.aborted).toBe(true);
});
