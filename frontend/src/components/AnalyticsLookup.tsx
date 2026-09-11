import { useEffect, useRef, useState, type FormEvent } from "react";
import { Alert, Button, Card, Input, Modal } from "./ui";

type Stats = {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  customAlias: boolean;
  clickCount: number;
  createdAt: string;
  lastAccessedAt: string | null;
  expiresAt: string | null;
};
type LookupState =
  | {
      kind: "empty" | "loading" | "error" | "missing" | "deleted";
      message?: string;
    }
  | { kind: "success"; stats: Stats };
const validCode = (value: string) => /^[a-zA-Z0-9_-]{1,16}$/.test(value);
const validDate = (value: unknown): value is string =>
  typeof value === "string" && Number.isFinite(Date.parse(value));
function validUrl(value: unknown): value is string {
  if (typeof value !== "string") return false;
  try {
    return ["http:", "https:"].includes(new URL(value).protocol);
  } catch {
    return false;
  }
}
function isStats(value: Stats, code: string): boolean {
  return (
    value != null &&
    value.shortCode === code &&
    validUrl(value.shortUrl) &&
    validUrl(value.originalUrl) &&
    typeof value.customAlias === "boolean" &&
    Number.isFinite(value.clickCount) &&
    value.clickCount >= 0 &&
    validDate(value.createdAt) &&
    (value.expiresAt === null || validDate(value.expiresAt)) &&
    (value.lastAccessedAt === null || validDate(value.lastAccessedAt))
  );
}
function Timestamp({ value, empty }: { value: string | null; empty: string }) {
  return value ? (
    <time dateTime={value} title={value}>
      {new Date(value).toLocaleString()}
    </time>
  ) : (
    <>{empty}</>
  );
}

/** Looks up one known link and deletes it only after explicit confirmation. */
export function AnalyticsLookup({
  initialCode = "",
}: {
  initialCode?: string;
}) {
  const [code, setCode] = useState(initialCode);
  const [state, setState] = useState<LookupState>({
    kind: initialCode && validCode(initialCode) ? "loading" : "empty",
  });
  const [fieldError, setFieldError] = useState("");
  const [confirmation, setConfirmation] = useState<Stats | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const request = useRef<AbortController | null>(null);
  const deleteLock = useRef(false);
  const active = useRef(true);
  const input = useRef<HTMLDivElement>(null);
  const [now, setNow] = useState(() => Date.now());

  async function load(value: string, abort: AbortController) {
    try {
      const response = await fetch(
        `/api/v1/urls/${encodeURIComponent(value)}`,
        { signal: abort.signal, cache: "no-store" },
      );
      if (abort.signal.aborted) return;
      if (response.status === 404) {
        setState({
          kind: "missing",
          message: `No link found for “${value}”. Check the code and try again.`,
        });
        return;
      }
      if (!response.ok) throw new Error("Lookup failed");
      const body = await response.json();
      if (!isStats(body, value)) throw new Error("Invalid analytics response");
      if (!abort.signal.aborted) {
        setNow(Date.now());
        setState({ kind: "success", stats: body });
      }
    } catch {
      if (!abort.signal.aborted)
        setState({
          kind: "error",
          message:
            "Couldn’t load analytics. Check your connection and try again.",
        });
    }
  }
  function lookup(value: string) {
    request.current?.abort();
    const abort = new AbortController();
    request.current = abort;
    setFieldError("");
    setState({ kind: "loading" });
    setConfirmation(null);
    setDeleteError("");
    void load(value, abort);
  }
  useEffect(() => {
    active.current = true;
    if (initialCode && validCode(initialCode)) {
      const abort = new AbortController();
      request.current = abort;
      // Defer startup so a discarded route mount cannot start a request.
      void Promise.resolve().then(() => {
        if (!abort.signal.aborted) return load(initialCode, abort);
      });
    }
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, [initialCode]);
  useEffect(() => {
    if (state.kind === "deleted")
      input.current?.querySelector("input")?.focus();
  }, [state.kind]);
  const expiry = state.kind === "success" ? state.stats.expiresAt : null;
  useEffect(() => {
    if (!expiry) return;
    // Refresh status while the page is open; cap the delay for distant expiry dates.
    let timer: ReturnType<typeof setTimeout>;
    const schedule = () => {
      const remaining = Date.parse(expiry) - Date.now();
      if (remaining <= 0) {
        setNow(Date.now());
        return;
      }
      timer = setTimeout(
        () => {
          setNow(Date.now());
          schedule();
        },
        Math.min(remaining, 2147483647),
      );
    };
    schedule();
    return () => clearTimeout(timer);
  }, [expiry]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (deleting || state.kind === "loading") return;
    const value = code.trim();
    if (!validCode(value)) {
      setFieldError(
        "Enter a short code of 1–16 letters, numbers, underscores, or hyphens.",
      );
      setState({ kind: "empty" });
      input.current?.querySelector("input")?.focus();
      return;
    }
    void lookup(value);
  }
  async function remove() {
    if (!confirmation || deleteLock.current) return;
    const target = confirmation.shortCode;
    deleteLock.current = true;
    setDeleting(true);
    setDeleteError("");
    try {
      const response = await fetch(
        `/api/v1/urls/${encodeURIComponent(target)}`,
        { method: "DELETE" },
      );
      if (!active.current) return;
      if (response.status !== 204 && response.status !== 404)
        throw new Error("Delete failed");
      setConfirmation(null);
      setState({
        kind: "deleted",
        message:
          response.status === 404
            ? `“${target}” no longer exists. Its analytics have been cleared.`
            : `“${target}” was permanently deleted.`,
      });
      setCode("");
      input.current?.querySelector("input")?.focus();
    } catch {
      if (active.current)
        setDeleteError(
          "Couldn’t confirm deletion. Check your connection and try again. Your link may already have been deleted.",
        );
    } finally {
      deleteLock.current = false;
      if (active.current) setDeleting(false);
    }
  }
  const stats = state.kind === "success" ? state.stats : null;
  return (
    <section className="analytics-page" aria-labelledby="analytics-title">
      <p className="eyebrow">EVERY CONNECTION COUNTS</p>
      <h1 id="analytics-title">Link analytics</h1>
      <p className="muted">
        Look up a short code to see its activity and details.
      </p>
      <Card>
        <form
          onSubmit={submit}
          noValidate
          aria-label="Look up link analytics"
          className="analytics-form"
        >
          <div ref={input}>
            <Input
              label="Short code"
              value={code}
              readOnly={deleting || state.kind === "loading"}
              onChange={(event) => {
                setCode(event.target.value);
                setFieldError("");
              }}
              error={fieldError}
              autoCapitalize="none"
              autoComplete="off"
              spellCheck={false}
              hint="Use the code after the final slash in your short link."
            />
          </div>
          <Button
            type="submit"
            loading={state.kind === "loading"}
            disabled={deleting}
          >
            {state.kind === "loading" ? "Loading analytics…" : "Look up link"}
          </Button>
        </form>
      </Card>
      {state.kind === "empty" && (
        <p className="muted">Enter a short code to get started.</p>
      )}
      {(state.kind === "error" || state.kind === "missing") && (
        <Alert tone="error">{state.message}</Alert>
      )}
      {state.kind === "deleted" && (
        <Alert tone="success">{state.message}</Alert>
      )}
      {stats && (
        <Card className="analytics-details">
          <h2>
            Details for <span>{stats.shortCode}</span>
          </h2>
          <p role="status">
            {stats.expiresAt && Date.parse(stats.expiresAt) <= now
              ? "Expired"
              : "Active"}{" "}
            · {stats.customAlias ? "Custom alias" : "Generated code"}
          </p>
          <p className="click-total">
            <strong>{stats.clickCount.toLocaleString()}</strong> total clicks
          </p>
          {stats.clickCount === 0 && <p className="muted">No visits yet.</p>}
          <dl className="result-metadata">
            <div>
              <dt>Short URL</dt>
              <dd>{stats.shortUrl}</dd>
            </div>
            <div>
              <dt>Original URL</dt>
              <dd>{stats.originalUrl}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>
                <Timestamp value={stats.createdAt} empty="Unavailable" />
              </dd>
            </div>
            <div>
              <dt>Last accessed</dt>
              <dd>
                <Timestamp value={stats.lastAccessedAt} empty="Never visited" />
              </dd>
            </div>
            <div>
              <dt>Expires</dt>
              <dd>
                <Timestamp value={stats.expiresAt} empty="Never expires" />
              </dd>
            </div>
          </dl>
          <Button
            variant="danger"
            onClick={() => {
              setConfirmation(stats);
              setDeleteError("");
            }}
          >
            Delete link
          </Button>
        </Card>
      )}
      <Modal
        open={!!confirmation}
        title="Delete this link?"
        onClose={() => {
          if (!deleteLock.current) setConfirmation(null);
        }}
      >
        <p>
          Delete <strong>{confirmation?.shortCode}</strong>? This permanently
          deletes the short link and its analytics. The short link will stop
          working. This cannot be undone.
        </p>
        {deleteError && <Alert tone="error">{deleteError}</Alert>}
        <div className="result-actions">
          <Button
            variant="secondary"
            disabled={deleting}
            onClick={() => setConfirmation(null)}
          >
            Cancel
          </Button>
          <Button variant="danger" loading={deleting} onClick={remove}>
            {deleting ? "Deleting…" : "Delete permanently"}
          </Button>
        </div>
      </Modal>
    </section>
  );
}
