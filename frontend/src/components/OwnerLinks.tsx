import { useEffect, useState } from "react";
import { managementRequest } from "../api/management";
import { ApiFailure, failureMessage, readJson } from "../api/request";
import { Alert, Button, Card } from "./ui";

type Link = { shortCode: string; originalUrl: string; clickCount: number };
type Page = {
  content: Link[];
  page: number;
  size: number;
  totalElements: number;
};

/** Lists only the authenticated owner's links using bounded server pagination. */
export function OwnerLinks({ apiKey }: { apiKey: string }) {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Page | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);
  useEffect(() => {
    const abort = new AbortController();
    async function load() {
      setBusy(true);
      setResult(null);
      setError("");
      try {
        if (!apiKey) throw new ApiFailure("unauthorized");
        const response = await managementRequest(
          apiKey,
          `?page=${page}&size=20`,
          { signal: abort.signal },
        );
        if (!response.ok) throw new ApiFailure("malformed");
        const data = await readJson(response);
        if (
          !Array.isArray(data.content) ||
          data.content.length > 20 ||
          data.page !== page ||
          data.size !== 20 ||
          !Number.isSafeInteger(data.totalElements) ||
          data.totalElements < 0 ||
          !data.content.every(
            (item: Link) =>
              item &&
              typeof item.shortCode === "string" &&
              /^[a-zA-Z0-9_-]{1,16}$/.test(item.shortCode) &&
              typeof item.originalUrl === "string" &&
              Number.isFinite(item.clickCount) &&
              item.clickCount >= 0,
          )
        )
          throw new ApiFailure("malformed");
        if (!abort.signal.aborted) setResult(data);
      } catch (failure) {
        if (!abort.signal.aborted) setError(failureMessage(failure));
      } finally {
        if (!abort.signal.aborted) setBusy(false);
      }
    }
    void Promise.resolve().then(() => {
      if (!abort.signal.aborted) return load();
    });
    return () => abort.abort();
  }, [apiKey, page, attempt]);
  return (
    <section className="analytics-page" aria-labelledby="owner-links-title">
      <h1 id="owner-links-title">My links</h1>
      <p className="muted">
        Your most recently created links. Open analytics to inspect or delete a
        link.
      </p>
      {busy && <p role="status">Loading your links…</p>}
      {error && <Alert tone="error">{error}</Alert>}
      <Button
        variant="secondary"
        disabled={busy}
        onClick={() => setAttempt((value) => value + 1)}
      >
        Refresh links
      </Button>
      {result && (
        <>
          <p role="status">
            {result.totalElements} links · Page {result.page + 1}
          </p>
          {result.content.length === 0 ? (
            <p>
              No links on this page. Create a link or return to the previous
              page.
            </p>
          ) : (
            <ul className="owner-links">
              {result.content.map((link) => (
                <li key={link.shortCode}>
                  <Card>
                    <h2>
                      <a
                        href={`/#/analytics?code=${encodeURIComponent(link.shortCode)}&mode=v2`}
                      >
                        {link.shortCode}
                      </a>
                    </h2>
                    <p className="owner-destination">{link.originalUrl}</p>
                    <p>{link.clickCount.toLocaleString()} total clicks</p>
                  </Card>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <div className="result-actions">
        <Button
          variant="secondary"
          disabled={busy || page === 0}
          onClick={() => setPage((value) => value - 1)}
        >
          Previous page
        </Button>
        <Button
          variant="secondary"
          disabled={busy || !result || (page + 1) * 20 >= result.totalElements}
          onClick={() => setPage((value) => value + 1)}
        >
          Next page
        </Button>
      </div>
    </section>
  );
}
