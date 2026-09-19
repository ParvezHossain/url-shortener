import { apiRequest, ApiFailure } from "./request";

/** Sends link-management requests to the selected API without falling back after authentication failure. */
export async function managementRequest(
  apiKey: string,
  suffix = "",
  init: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.delete("X-API-Key");
  if (apiKey) headers.set("X-API-Key", apiKey);
  const response = await apiRequest(
    `/api/${apiKey ? "v2" : "v1"}/urls${suffix}`,
    {
      ...init,
      headers,
      cache: "no-store",
    },
  );
  if (response.status === 401) throw new ApiFailure("unauthorized");
  if (response.status === 403) throw new ApiFailure("forbidden");
  return response;
}
