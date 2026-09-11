import {
  apiRequest,
  readJson,
  ApiFailure,
  failureMessage,
} from "../api/request";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { CreationResult, type CreatedLink } from "./CreationResult";
import { Alert, Button, Card, Input } from "./ui";

type Fields = { originalUrl: string; customAlias: string; expiresAt: string };
type Errors = Partial<Fields>;
const emptyFields: Fields = { originalUrl: "", customAlias: "", expiresAt: "" };
const retryMessage =
  "We couldn’t confirm that your link was created. Your input is saved below. Please try again. If you chose an alias, it may already have been created.";

function validHttpUrl(value: unknown): value is string {
  if (typeof value !== "string" || /\s/.test(value)) return false;
  try {
    const url = new URL(value);
    return (
      /^(https?:\/\/)/i.test(value) &&
      ["http:", "https:"].includes(url.protocol) &&
      !!url.hostname
    );
  } catch {
    return false;
  }
}

function validate(fields: Fields): Errors {
  const errors: Errors = {};
  if (!fields.originalUrl.trim())
    errors.originalUrl = "Enter the URL you want to shorten.";
  else if (!validHttpUrl(fields.originalUrl.trim()))
    errors.originalUrl = "Enter a complete http:// or https:// URL.";
  if (fields.customAlias && !/^[a-zA-Z0-9_-]{3,16}$/.test(fields.customAlias))
    errors.customAlias = "Use 3–16 letters, numbers, underscores, or hyphens.";
  if (
    fields.expiresAt &&
    (!Number.isFinite(new Date(fields.expiresAt).getTime()) ||
      new Date(fields.expiresAt).getTime() <= Date.now())
  )
    errors.expiresAt = "Choose a future date and time.";
  return errors;
}

/** Creates short links while preserving input and mapping API feedback to accessible fields. */
export function CreateUrlForm() {
  const [fields, setFields] = useState<Fields>(emptyFields);
  const [errors, setErrors] = useState<Errors>({});
  const [message, setMessage] = useState("");
  const [result, setResult] = useState<CreatedLink | null>(null);
  const resetFocus = useRef(false);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const form = useRef<HTMLFormElement>(null);
  const [baseUrl, setBaseUrl] = useState("");
  const [configError, setConfigError] = useState(false);
  const [configAttempt, setConfigAttempt] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    async function loadConfig() {
      try {
        const response = await apiRequest("/ui/config", {
          signal: abort.signal,
        });
        if (!response.ok) throw new Error("Configuration unavailable");
        const config = await readJson(response);
        if (!validHttpUrl(config.publicBaseUrl))
          throw new Error("Invalid public URL");
        if (!abort.signal.aborted)
          setBaseUrl(config.publicBaseUrl.replace(/\/+$/, "") + "/");
      } catch {
        if (!abort.signal.aborted) setConfigError(true);
      }
    }
    void loadConfig();
    return () => abort.abort();
  }, [configAttempt]);

  useEffect(() => {
    if (!result && resetFocus.current) {
      form.current
        ?.querySelector<HTMLInputElement>('[name="originalUrl"]')
        ?.focus();
      resetFocus.current = false;
    }
  }, [result]);

  function showErrors(next: Errors, detail: string) {
    setErrors(next);
    setMessage(detail);
    const first = Object.keys(next)[0];
    if (next.customAlias || next.expiresAt) {
      const details = form.current?.querySelector("details");
      if (details) details.open = true;
    }
    if (first)
      form.current
        ?.querySelector<HTMLInputElement>(`[name="${first}"]`)
        ?.focus();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) return;
    setResult(null);
    const next = validate(fields);
    if (Object.keys(next).length) {
      showErrors(next, "Check the highlighted fields and try again.");
      return;
    }
    inFlight.current = true;
    setPending(true);
    setErrors({});
    setMessage("");
    try {
      const response = await apiRequest("/api/v1/urls", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, application/problem+json",
        },
        body: JSON.stringify({
          originalUrl: fields.originalUrl.trim(),
          ...(fields.customAlias ? { customAlias: fields.customAlias } : {}),
          ...(fields.expiresAt
            ? { expiresAt: new Date(fields.expiresAt).toISOString() }
            : {}),
        }),
      });
      const body = await readJson(response);
      if (response.ok) {
        if (
          !validHttpUrl(body.shortUrl) ||
          !validHttpUrl(body.originalUrl) ||
          typeof body.shortCode !== "string" ||
          !/^[a-zA-Z0-9_-]{1,16}$/.test(body.shortCode) ||
          typeof body.createdAt !== "string" ||
          !Number.isFinite(Date.parse(body.createdAt)) ||
          (body.expiresAt !== null &&
            (typeof body.expiresAt !== "string" ||
              !Number.isFinite(Date.parse(body.expiresAt))))
        )
          throw new ApiFailure("malformed");
        setResult({ ...body, customAlias: Boolean(fields.customAlias) });
      } else if (response.status === 409) {
        showErrors(
          { customAlias: "This alias is already taken. Choose another one." },
          "Choose a different alias and try again.",
        );
      } else if (response.status === 400) {
        const fieldErrors: Errors = {};
        if (Array.isArray(body.errors)) {
          for (const error of body.errors) {
            if (typeof error !== "string") continue;
            const match = /^(originalUrl|customAlias|expiresAt):\s*(.+)$/.exec(
              error,
            );
            if (match) fieldErrors[match[1] as keyof Fields] = match[2];
          }
        }
        const detail =
          typeof body.detail === "string"
            ? body.detail
            : "Check your URL and optional settings, then try again.";
        if (!Object.keys(fieldErrors).length) {
          if (/expiresAt/i.test(detail)) fieldErrors.expiresAt = detail;
          else if (/alias/i.test(detail)) fieldErrors.customAlias = detail;
          else if (/url/i.test(detail)) fieldErrors.originalUrl = detail;
        }
        showErrors(fieldErrors, detail);
      } else {
        setMessage(retryMessage);
      }
    } catch (error) {
      setMessage(failureMessage(error) + " " + retryMessage);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  function update(name: keyof Fields, value: string) {
    setFields((previous) => ({ ...previous, [name]: value }));
    setErrors((previous) => ({ ...previous, [name]: undefined }));
    setResult(null);
  }

  if (result)
    return (
      <CreationResult
        result={result}
        onReset={() => {
          resetFocus.current = true;
          setFields(emptyFields);
          setErrors({});
          setMessage("");
          setPending(false);
          setResult(null);
        }}
      />
    );

  return (
    <Card className="creation-card">
      <p className="eyebrow">READY WHEN YOU ARE</p>
      <h2 id="create-title">Make a shorter connection.</h2>
      <form
        ref={form}
        noValidate
        aria-labelledby="create-title"
        onSubmit={submit}
      >
        <Input
          readOnly={pending}
          label="Destination URL"
          name="originalUrl"
          type="url"
          inputMode="url"
          autoComplete="url"
          placeholder="https://example.com/your-long-link"
          required
          value={fields.originalUrl}
          onChange={(event) => update("originalUrl", event.target.value)}
          error={errors.originalUrl}
          hint="A complete URL starting with http:// or https://."
        />
        <details className="optional-settings">
          <summary>
            Customize your link <span className="muted">Optional</span>
          </summary>
          <div className="alias-field">
            {!baseUrl && (
              <p className="public-prefix" id="public-prefix">
                {configError
                  ? "Public link prefix unavailable."
                  : "Loading public link prefix…"}
              </p>
            )}
            {configError && (
              <Button
                variant="secondary"
                onClick={() => {
                  setConfigError(false);
                  setConfigAttempt((value) => value + 1);
                }}
              >
                Retry loading prefix
              </Button>
            )}
            <Input
              readOnly={pending}
              label="Custom alias (optional)"
              name="customAlias"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              value={fields.customAlias}
              onChange={(event) => update("customAlias", event.target.value)}
              error={errors.customAlias}
              prefix={baseUrl}
              aria-describedby={baseUrl ? undefined : "public-prefix"}
              hint="3–16 characters: letters, numbers, underscores (_) or hyphens (-). Aliases are case-sensitive."
            />
          </div>
          <Input
            readOnly={pending}
            label="Expiry (optional)"
            name="expiresAt"
            type="datetime-local"
            value={fields.expiresAt}
            onChange={(event) => update("expiresAt", event.target.value)}
            error={errors.expiresAt}
            hint="Your local date and time. Leave blank for a link that never expires."
          />
        </details>
        {message && <Alert tone="error">{message}</Alert>}
        <Button type="submit" loading={pending} className="create-submit">
          {pending ? "Creating your link…" : "Shorten link"}
          <span aria-hidden="true">↗</span>
        </Button>
      </form>
    </Card>
  );
}
