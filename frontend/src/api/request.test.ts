import { afterEach, expect, test, vi } from "vitest";
import { apiRequest, ApiFailure, failureMessage, readJson } from "./request";

afterEach(() => vi.useRealTimers());
test("apiRequest_success_returnsBufferedResponse", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response('{"ok":true}')),
  );
  expect(await readJson(await apiRequest("/api/test"))).toEqual({ ok: true });
});
test.each([
  [429, "rate-limit"],
  [500, "server"],
  [503, "server"],
] as const)("apiRequest_status%s_usesSafeMessage", async (status, kind) => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response("secret stack trace", { status })),
  );
  await expect(apiRequest("/api/test")).rejects.toMatchObject({ kind });
});
test("apiRequest_offline_doesNotSendRequest", async () => {
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
  const fetcher = vi.fn();
  vi.stubGlobal("fetch", fetcher);
  await expect(apiRequest("/api/test")).rejects.toMatchObject({
    kind: "offline",
  });
  expect(fetcher).not.toHaveBeenCalled();
});
test("apiRequest_timeout_abortsRequestIncludingBodyAndDoesNotRetry", async () => {
  vi.useFakeTimers();
  const fetcher = vi
    .fn()
    .mockResolvedValue({ status: 200, text: () => new Promise(() => {}) });
  vi.stubGlobal("fetch", fetcher);
  const outcome = apiRequest("/api/test", { method: "POST" }, 20).catch(
    (error) => error,
  );
  await vi.advanceTimersByTimeAsync(20);
  expect(await outcome).toMatchObject({ kind: "timeout" });
  expect(fetcher).toHaveBeenCalledOnce();
  expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
});
test("apiRequest_callerCancels_preservesAbortSemantics", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => new Promise(() => {})),
  );
  const abort = new AbortController();
  const outcome = apiRequest("/api/test", { signal: abort.signal }).catch(
    (error) => error,
  );
  abort.abort();
  expect(await outcome).toMatchObject({ name: "AbortError" });
});
test("readJson_malformedBody_hidesParserDetails", async () => {
  await expect(
    readJson(new Response("secret server trace")),
  ).rejects.toMatchObject({ kind: "malformed" });
});
test("failureMessage_unexpectedException_hidesDetails", () => {
  expect(failureMessage(new Error("secret"))).toContain(
    "Check your connection",
  );
  expect(failureMessage(new ApiFailure("timeout"))).toContain("too long");
});
