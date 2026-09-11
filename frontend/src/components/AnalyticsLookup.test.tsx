import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { AnalyticsLookup } from "./AnalyticsLookup";

const stats = {
  shortCode: "known",
  shortUrl: "https://links.example/known",
  originalUrl: "https://example.com/destination",
  createdAt: "2026-01-01T12:00:00Z",
  expiresAt: null as string | null,
  lastAccessedAt: null as string | null,
  clickCount: 0,
  customAlias: true,
};
const api = vi.fn();
const reply = (body = stats) =>
  new Response(JSON.stringify(body), { status: 200 });
beforeEach(() => {
  api.mockReset();
  vi.stubGlobal("fetch", api);
});
async function loaded() {
  api.mockResolvedValueOnce(reply());
  render(<AnalyticsLookup initialCode="known" />);
  await screen.findByRole("heading", { name: "Details for known" });
}

test("AnalyticsLookup_existingCode_rendersStatsWithoutIncrementingClicks", async () => {
  await loaded();
  expect(api).toHaveBeenCalledWith(
    "/api/v1/urls/known",
    expect.objectContaining({ cache: "no-store" }),
  );
  expect(api).toHaveBeenCalledOnce();
  expect(screen.getByText(stats.shortUrl)).toBeVisible();
  expect(screen.getByText(stats.originalUrl)).toBeVisible();
  expect(screen.getByText("No visits yet.")).toBeVisible();
  expect(screen.getByText("Never visited")).toBeVisible();
  expect(screen.getByText("Never expires")).toBeVisible();
  expect(screen.getByRole("status")).toHaveTextContent("Active · Custom alias");
  expect(screen.getByTitle(stats.createdAt)).toHaveAttribute(
    "datetime",
    stats.createdAt,
  );
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});

test("AnalyticsLookup_unknownCode_rendersNotFoundState", async () => {
  api.mockResolvedValue(new Response("", { status: 404 }));
  render(<AnalyticsLookup initialCode="missing" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("No link found");
  expect(
    screen.queryByRole("button", { name: "Delete link" }),
  ).not.toBeInTheDocument();
});

test("AnalyticsLookup_expiredCode_rendersExpiredState", async () => {
  api.mockResolvedValue(
    reply({
      ...stats,
      expiresAt: "2000-01-01T00:00:00Z",
      lastAccessedAt: "1999-12-31T00:00:00Z",
      clickCount: 12,
      customAlias: false,
    }),
  );
  render(<AnalyticsLookup initialCode="known" />);
  await waitFor(() =>
    expect(screen.getByRole("status")).toHaveTextContent(
      "Expired · Generated code",
    ),
  );
  expect(screen.getByText("12")).toBeVisible();
  expect(screen.getByTitle("1999-12-31T00:00:00Z")).toBeVisible();
});

test("AnalyticsLookup_emptyAndInvalidInput_doNotRequestData", async () => {
  render(<AnalyticsLookup />);
  expect(screen.getByText("Enter a short code to get started.")).toBeVisible();
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Look up link" }));
  expect(screen.getByLabelText("Short code")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api).not.toHaveBeenCalled();
});

test("AnalyticsLookup_networkError_retainsCodeAndCanRetry", async () => {
  api
    .mockRejectedValueOnce(new Error("Offline"))
    .mockResolvedValueOnce(reply());
  render(<AnalyticsLookup initialCode="known" />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Check your connection",
  );
  expect(screen.getByLabelText("Short code")).toHaveValue("known");
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Look up link" }));
  expect(
    await screen.findByRole("heading", { name: "Details for known" }),
  ).toBeVisible();
});

test("AnalyticsLookup_loading_clearsOldStatsAndPreventsDuplicateRequests", async () => {
  await loaded();
  let resolve!: (response: Response) => void;
  api.mockReturnValueOnce(
    new Promise<Response>((done) => {
      resolve = done;
    }),
  );
  fireEvent.change(screen.getByLabelText("Short code"), {
    target: { value: "next" },
  });
  fireEvent.submit(screen.getByRole("form"));
  fireEvent.submit(screen.getByRole("form"));
  expect(
    screen.getByRole("button", { name: /Loading analytics/ }),
  ).toBeDisabled();
  expect(
    screen.queryByRole("heading", { name: "Details for known" }),
  ).not.toBeInTheDocument();
  expect(api).toHaveBeenCalledTimes(2);
  await act(async () => resolve(reply({ ...stats, shortCode: "next" })));
});

test("DeleteLink_cancelled_doesNotCallApi", async () => {
  await loaded();
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Delete link" }));
  const dialog = screen.getByRole("dialog", { name: "Delete this link?" });
  expect(dialog).toHaveTextContent("known");
  expect(dialog).toHaveTextContent("cannot be undone");
  await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
  expect(api).toHaveBeenCalledOnce();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "Details for known" }),
  ).toBeVisible();
});

test("DeleteLink_confirmed_deletesAndClearsStats", async () => {
  await loaded();
  api.mockResolvedValueOnce(new Response(null, { status: 204 }));
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Delete link" }));
  expect(api).toHaveBeenCalledOnce();
  await user.click(screen.getByRole("button", { name: "Delete permanently" }));
  await waitFor(() =>
    expect(screen.getByRole("status")).toHaveTextContent("permanently deleted"),
  );
  expect(api).toHaveBeenLastCalledWith(
    "/api/v1/urls/known",
    expect.objectContaining({ method: "DELETE" }),
  );
  expect(screen.queryByText(stats.shortUrl)).not.toBeInTheDocument();
  expect(screen.getByLabelText("Short code")).toHaveValue("");
  expect(screen.getByLabelText("Short code")).toHaveFocus();
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("DeleteLink_failed_keepsDetailsAndAllowsExplicitRetry", async () => {
  await loaded();
  api
    .mockRejectedValueOnce(new Error("Offline"))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Delete link" }));
  await user.click(screen.getByRole("button", { name: "Delete permanently" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Couldn’t confirm deletion",
  );
  expect(screen.getByText(stats.shortUrl)).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Delete permanently" }));
  await waitFor(() =>
    expect(screen.queryByText(stats.shortUrl)).not.toBeInTheDocument(),
  );
});

test("DeleteLink_alreadyAbsent_clearsStaleDetails", async () => {
  await loaded();
  api.mockResolvedValueOnce(new Response(null, { status: 404 }));
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Delete link" }));
  await user.click(screen.getByRole("button", { name: "Delete permanently" }));
  await waitFor(() =>
    expect(screen.getByRole("status")).toHaveTextContent("no longer exists"),
  );
  expect(screen.queryByText(stats.shortUrl)).not.toBeInTheDocument();
});

test("DeleteLink_pending_preventsRepeatedDeletionButEscapeDismissesDialog", async () => {
  await loaded();
  let resolve!: (response: Response) => void;
  api.mockReturnValueOnce(
    new Promise<Response>((done) => {
      resolve = done;
    }),
  );
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Delete link" }));
  const confirm = screen.getByRole("button", { name: "Delete permanently" });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  expect(api).toHaveBeenCalledTimes(2);
  expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
  fireEvent(
    screen.getByRole("dialog"),
    new Event("cancel", { cancelable: true }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await act(async () => resolve(new Response(null, { status: 204 })));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

test("AnalyticsLookup_routeUnmount_abortsLookupWithoutRestoringStaleData", async () => {
  let resolve!: (response: Response) => void;
  api.mockReturnValueOnce(
    new Promise<Response>((done) => {
      resolve = done;
    }),
  );
  const view = render(<AnalyticsLookup initialCode="known" />);
  await waitFor(() => expect(api).toHaveBeenCalledOnce());
  const signal = api.mock.calls[0][1].signal as AbortSignal;
  view.unmount();
  render(<AnalyticsLookup />);
  await act(async () => resolve(reply()));
  expect(signal.aborted).toBe(true);
  expect(screen.queryByText(stats.originalUrl)).not.toBeInTheDocument();
});

test("AnalyticsLookup_expiryReached_updatesStatusWithoutAnotherRequest", async () => {
  vi.useFakeTimers();
  try {
    vi.setSystemTime(new Date("2026-01-01T00:00:00Z"));
    api.mockResolvedValue(
      reply({ ...stats, expiresAt: "2026-01-01T00:00:01Z" }),
    );
    render(<AnalyticsLookup initialCode="known" />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByRole("status")).toHaveTextContent("Active");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(screen.getByRole("status")).toHaveTextContent("Expired");
    expect(api).toHaveBeenCalledOnce();
  } finally {
    vi.useRealTimers();
  }
});
