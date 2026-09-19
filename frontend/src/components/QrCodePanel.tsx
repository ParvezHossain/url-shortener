import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  apiRequest,
  ApiFailure,
  failureMessage,
  readJson,
} from "../api/request";
import { Alert, Button, Card, Input } from "./ui";

type Preview = { png: string; svg: string; shortUrl: string; code: string };

/** Previews and downloads owned QR images using a credential held only in memory. */
export function QrCodePanel({ apiKey = "" }: { apiKey?: string }) {
  const [code, setCode] = useState("");
  const [key, setKey] = useState("");
  const [size, setSize] = useState("256");
  const [margin, setMargin] = useState("4");
  const [correction, setCorrection] = useState("M");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const activeRequest = useRef<AbortController | null>(null);
  useEffect(() => () => activeRequest.current?.abort(), []);
  useEffect(
    () => () => {
      if (preview) {
        URL.revokeObjectURL(preview.png);
        URL.revokeObjectURL(preview.svg);
      }
    },
    [preview],
  );

  function changed() {
    setPreview(null);
    setError("");
  }
  async function generate(event: FormEvent) {
    event.preventDefault();
    if (activeRequest.current) return;
    changed();
    if (!/^[a-zA-Z0-9_-]{1,16}$/.test(code.trim()) || !(apiKey || key).trim()) {
      setError("Enter an owned short code and its owner's API key.");
      return;
    }
    if (
      !/^\d+$/.test(size) ||
      Number(size) < 128 ||
      Number(size) > 1024 ||
      !/^\d+$/.test(margin) ||
      Number(margin) < 4 ||
      Number(margin) > 8
    ) {
      setError(
        "Choose a size from 128 to 1024 pixels and a margin from 4 to 8 modules.",
      );
      return;
    }
    const abort = new AbortController();
    activeRequest.current = abort;
    setBusy(true);
    const init: RequestInit = {
      headers: { "X-API-Key": (apiKey || key).trim() },
      cache: "no-store",
      signal: abort.signal,
    };
    const target = code.trim();
    const path = `/api/v2/urls/${encodeURIComponent(target)}`;
    async function checked(url: string) {
      const response = await apiRequest(url, init);
      if (response.status === 401)
        throw new Error("Enter a valid API key and try again.");
      if (response.status === 404)
        throw new Error("No owned link found for this code.");
      if (response.status === 400)
        throw new Error(
          "These QR options cannot encode this link. Try a larger size.",
        );
      if (!response.ok) throw new ApiFailure("malformed");
      return response;
    }
    try {
      const metadata = await readJson(await checked(path));
      if (
        metadata.shortCode !== target ||
        typeof metadata.shortUrl !== "string" ||
        !/^https?:$/.test(new URL(metadata.shortUrl).protocol)
      )
        throw new ApiFailure("malformed");
      const query = new URLSearchParams({ size, margin, correction });
      const [png, svg] = await Promise.all([
        checked(`${path}/qr.png?${query}`),
        checked(`${path}/qr.svg?${query}`),
      ]);
      if (
        png.headers.get("Content-Type")?.split(";")[0] !== "image/png" ||
        svg.headers.get("Content-Type")?.split(";")[0] !== "image/svg+xml"
      )
        throw new ApiFailure("malformed");
      const [pngBlob, svgBlob] = await Promise.all([png.blob(), svg.blob()]);
      if (abort.signal.aborted) return;
      setPreview({
        png: URL.createObjectURL(pngBlob),
        svg: URL.createObjectURL(svgBlob),
        shortUrl: metadata.shortUrl,
        code: target,
      });
    } catch (failure) {
      if (!abort.signal.aborted) {
        const safeMessages = [
          "Enter a valid API key and try again.",
          "No owned link found for this code.",
          "These QR options cannot encode this link. Try a larger size.",
        ];
        setError(
          failure instanceof Error && safeMessages.includes(failure.message)
            ? failure.message
            : failureMessage(failure),
        );
      }
    } finally {
      if (!abort.signal.aborted) setBusy(false);
      activeRequest.current = null;
    }
  }
  return (
    <section className="analytics-page qr-page" aria-labelledby="qr-title">
      <h1 id="qr-title">QR codes</h1>
      <p className="muted">
        Preview and download a QR code for a link you own. Your API key stays in
        memory while this page is open.
      </p>
      <Card>
        <form
          className="qr-form"
          aria-label="Generate QR code"
          onSubmit={generate}
          noValidate
        >
          <Input
            label="Owned short code"
            value={code}
            disabled={busy}
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            onChange={(e) => {
              setCode(e.target.value);
              changed();
            }}
          />
          {!apiKey && (
            <Input
              label="API key"
              type="password"
              value={key}
              disabled={busy}
              autoComplete="off"
              spellCheck={false}
              onChange={(e) => {
                setKey(e.target.value);
                changed();
              }}
            />
          )}
          <div className="qr-illustration" aria-hidden="true">
            <div className="scan-frame">
              <span />
              <span />
              <span />
              <i>↗</i>
            </div>
          </div>
          <Input
            label="Image size (pixels)"
            type="number"
            min={128}
            max={1024}
            value={size}
            disabled={busy}
            onChange={(e) => {
              setSize(e.target.value);
              changed();
            }}
          />
          <Input
            label="Quiet margin (modules)"
            type="number"
            min={4}
            max={8}
            value={margin}
            disabled={busy}
            onChange={(e) => {
              setMargin(e.target.value);
              changed();
            }}
          />
          <label className="field">
            Error correction
            <select
              value={correction}
              disabled={busy}
              onChange={(e) => {
                setCorrection(e.target.value);
                changed();
              }}
            >
              <option value="L">Low (L)</option>
              <option value="M">Medium (M)</option>
              <option value="Q">Quartile (Q)</option>
              <option value="H">High (H)</option>
            </select>
          </label>
          <Button type="submit" loading={busy}>
            {busy ? "Generating…" : "Preview QR code"}
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => {
              setKey("");
              changed();
            }}
          >
            {apiKey ? "Clear preview" : "Clear API key and preview"}
          </Button>
        </form>
      </Card>
      {error && <Alert tone="error">{error}</Alert>}
      {preview && (
        <Card className="qr-preview">
          <h2>QR code for {preview.code}</h2>
          <img
            src={preview.png}
            alt={`QR code for ${preview.shortUrl}`}
            width={Number(size)}
            height={Number(size)}
          />
          <p>
            Encoded short URL:{" "}
            <a
              href={preview.shortUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              {preview.shortUrl}
            </a>
          </p>
          <div className="result-actions">
            <a
              className="button button--secondary"
              href={preview.png}
              download={`${preview.code}-qr.png`}
            >
              Download PNG
            </a>
            <a
              className="button button--secondary"
              href={preview.svg}
              download={`${preview.code}-qr.svg`}
            >
              Download SVG
            </a>
          </div>
        </Card>
      )}
    </section>
  );
}
