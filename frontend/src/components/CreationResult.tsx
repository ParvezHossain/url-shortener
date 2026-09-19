import { useEffect, useRef, useState } from "react";
import { Button, Card, Input } from "./ui";

/** Carries server-confirmed creation metadata and the submitted alias choice. */
export type CreatedLink = {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
  customAlias: boolean;
};

/** Presents a created link with accessible copy, sharing, and next-step actions. */
export function CreationResult({
  result,
  onReset,
  authenticated = false,
}: {
  result: CreatedLink;
  onReset: () => void;
  authenticated?: boolean;
}) {
  const [status, setStatus] = useState("");
  const [manualCopy, setManualCopy] = useState(false);
  const [copying, setCopying] = useState(false);
  const [sharing, setSharing] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const copyInFlight = useRef(false);
  const shareInFlight = useRef(false);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    heading.current?.focus();
    // Populate the live region after it is mounted so assistive technology observes the update.
    const announcement = requestAnimationFrame(() =>
      setStatus((previous) => previous || "Your short link is ready."),
    );
    return () => {
      cancelAnimationFrame(announcement);
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    if (manualCopy) {
      const input = panel.current?.querySelector<HTMLInputElement>("input");
      input?.focus();
      input?.select();
    }
  }, [manualCopy]);

  async function copy() {
    if (copyInFlight.current) return;
    copyInFlight.current = true;
    setCopying(true);
    setStatus("");
    try {
      if (!navigator.clipboard?.writeText)
        throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(result.shortUrl);
      if (mounted.current) {
        setManualCopy(false);
        setStatus("Short link copied to clipboard.");
      }
    } catch {
      if (mounted.current) {
        setManualCopy(true);
        setStatus(
          "Select the short link below and copy it using your device’s copy command.",
        );
      }
    } finally {
      copyInFlight.current = false;
      if (mounted.current) setCopying(false);
    }
  }

  async function share() {
    if (shareInFlight.current) return;
    shareInFlight.current = true;
    setSharing(true);
    setStatus("");
    try {
      await navigator.share({ title: "Short link", url: result.shortUrl });
      if (mounted.current) setStatus("Link shared.");
    } catch (error) {
      if (mounted.current)
        setStatus(
          error instanceof DOMException && error.name === "AbortError"
            ? "Sharing cancelled. Your link is still ready."
            : "Sharing didn’t work. You can copy the link instead.",
        );
    } finally {
      shareInFlight.current = false;
      if (mounted.current) setSharing(false);
    }
  }

  return (
    <Card className="creation-card creation-result">
      <div ref={panel}>
        <p className="eyebrow">READY TO GO PLACES</p>
        <h2 ref={heading} tabIndex={-1}>
          Your short link is ready.
        </h2>
        <a
          className="result-link"
          href={result.shortUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          {result.shortUrl}
        </a>
        <div className="result-actions">
          <Button onClick={copy} loading={copying}>
            {copying ? "Copying…" : "Copy link"}
          </Button>
          {typeof navigator.share === "function" && (
            <Button variant="secondary" onClick={share} loading={sharing}>
              {sharing ? "Sharing…" : "Share link"}
            </Button>
          )}
        </div>
        <p
          role="status"
          aria-live="polite"
          aria-atomic="true"
          className="result-status"
        >
          {status}
        </p>
        {manualCopy && (
          <Input
            label="Copy short link manually"
            value={result.shortUrl}
            readOnly
            onFocus={(event) => event.target.select()}
            hint="Use Ctrl+C, Command+C, or your device’s selection menu."
          />
        )}
        <dl className="result-metadata">
          <div>
            <dt>Original URL</dt>
            <dd>{result.originalUrl}</dd>
          </div>
          <div>
            <dt>Link type</dt>
            <dd>{result.customAlias ? "Custom alias" : "Generated code"}</dd>
          </div>
          <div>
            <dt>Expiry</dt>
            <dd>
              {result.expiresAt ? (
                <>
                  <span>Expires </span>
                  <time dateTime={result.expiresAt} title={result.expiresAt}>
                    {new Date(result.expiresAt).toLocaleString()}
                  </time>
                </>
              ) : (
                "Never expires"
              )}
            </dd>
          </div>
        </dl>
        <div className="result-actions">
          <a
            className="button button--secondary"
            href={result.shortUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            Open link <span className="sr-only">(new tab)</span>
            <span aria-hidden="true">↗</span>
          </a>
          <a
            className="button button--secondary"
            href={`/#/analytics?code=${encodeURIComponent(result.shortCode)}${authenticated ? "&mode=v2" : ""}`}
            target={authenticated ? undefined : "_blank"}
            rel="noopener noreferrer"
          >
            View analytics{" "}
            {!authenticated && <span className="sr-only">(new tab)</span>}
          </a>
        </div>
        <Button className="create-submit" variant="secondary" onClick={onReset}>
          Shorten another
        </Button>
      </div>
    </Card>
  );
}
