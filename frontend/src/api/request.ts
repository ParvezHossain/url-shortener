/** Describes safe, user-facing failures without exposing response bodies or exception details. */
export class ApiFailure extends Error {
  /** Selects a recovery message for a known transport or response failure. */
  constructor(
    public readonly kind:
      | "offline"
      | "timeout"
      | "malformed"
      | "rate-limit"
      | "server"
      | "network"
      | "unauthorized"
      | "forbidden",
  ) {
    const messages = {
      unauthorized:
        "Your API key was rejected. Update it in API access and try again.",
      forbidden: "This API key does not have permission for that action.",
      offline: "You’re offline. Reconnect and try again.",
      timeout: "The request took too long. Please try again.",
      malformed:
        "The server returned an unexpected response. Please try again.",
      "rate-limit": "Too many requests. Wait a moment before trying again.",
      server:
        "The service is temporarily unavailable. Please try again shortly.",
      network:
        "Couldn’t reach the service. Check your connection and try again.",
    };
    super(messages[kind]);
  }
}

function isOffline() {
  return navigator.onLine === false;
}

/** Fetches and buffers a response within a deadline, respecting route cancellation without retrying writes. */
export async function apiRequest(
  url: string,
  init: RequestInit = {},
  timeoutMs = 15000,
): Promise<Response> {
  if (isOffline()) throw new ApiFailure("offline");
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let cancel = () => {};
  try {
    const deadline = new Promise<Response>((_resolve, reject) => {
      cancel = () => {
        controller.abort();
        reject(new DOMException("Cancelled", "AbortError"));
      };
      if (init.signal?.aborted) {
        cancel();
        return;
      }
      init.signal?.addEventListener("abort", cancel, { once: true });
      timer = setTimeout(() => {
        reject(new ApiFailure("timeout"));
        controller.abort();
      }, timeoutMs);
    });
    const operation = async () => {
      if (controller.signal.aborted)
        throw new DOMException("Cancelled", "AbortError");
      const response = await fetch(url, { ...init, signal: controller.signal });
      if (response.status === 429) throw new ApiFailure("rate-limit");
      if (response.status >= 500) throw new ApiFailure("server");
      const body = await response.arrayBuffer();
      return new Response(body.byteLength ? body : null, {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
      });
    };
    return await Promise.race([deadline, operation()]);
  } catch (error) {
    if (init.signal?.aborted) throw error;
    if (error instanceof ApiFailure) throw error;
    throw new ApiFailure(isOffline() ? "offline" : "network");
  } finally {
    clearTimeout(timer);
    init.signal?.removeEventListener("abort", cancel);
    controller.abort();
  }
}

/** Parses JSON without revealing parser diagnostics or an untrusted response body. */
export async function readJson(response: Response) {
  try {
    const body = await response.json();
    if (body === null || typeof body !== "object" || Array.isArray(body))
      throw new Error();
    return body;
  } catch {
    throw new ApiFailure("malformed");
  }
}

/** Returns only a known safe message, replacing unexpected exception text. */
export function failureMessage(error: unknown): string {
  return error instanceof ApiFailure
    ? error.message
    : new ApiFailure("network").message;
}
