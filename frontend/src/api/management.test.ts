import { expect, test, vi } from "vitest";
import { managementRequest } from "./management";

test("managementRequest_selectedVersion_sendsKeyOnlyToV2", async () => {
  const fetch = vi
    .fn()
    .mockImplementation(() => Promise.resolve(new Response("{}")));
  vi.stubGlobal("fetch", fetch);
  await managementRequest("secret", "/owned", { method: "DELETE" });
  expect(fetch.mock.calls[0][0]).toBe("/api/v2/urls/owned");
  expect(fetch.mock.calls[0][1].headers.get("X-API-Key")).toBe("secret");
  await managementRequest("", "/legacy", {
    headers: { "X-API-Key": "stale credential" },
  });
  expect(fetch.mock.calls[1][0]).toBe("/api/v1/urls/legacy");
  expect(fetch.mock.calls[1][1].headers.has("X-API-Key")).toBe(false);
});

test.each([401, 403])(
  "managementRequest_authFailure_%s_neverFallsBackOrLeaksBody",
  async (status) => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response("private server diagnostics", { status }),
      );
    vi.stubGlobal("fetch", fetch);
    await expect(managementRequest("secret")).rejects.toMatchObject({
      kind: status === 401 ? "unauthorized" : "forbidden",
    });
    expect(fetch).toHaveBeenCalledTimes(1);
  },
);
