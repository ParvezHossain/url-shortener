import { useState, type FormEvent } from "react";
import { Alert, Button, Input } from "./ui";

/** Selects anonymous or owner-scoped management without persisting credentials. */
export function ApiAccess({
  mode,
  connected,
  onChange,
}: {
  mode: "v1" | "v2";
  connected: boolean;
  onChange: (mode: "v1" | "v2", key: string) => void;
}) {
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  function submit(event: FormEvent) {
    event.preventDefault();
    const key = draft.trim();
    if (!/^usk_[0-9a-f]{24}_[0-9a-f]{64}$/.test(key)) {
      setError("Enter the complete operator-issued API key (usk_…).");
      return;
    }
    onChange("v2", key);
    setDraft("");
    setError("");
  }
  return (
    <details className="api-access">
      <summary>
        API access ·{" "}
        {mode === "v1"
          ? "Legacy V1"
          : connected
            ? "V2 key set"
            : "V2 key required"}
      </summary>
      <p>
        Use an operator-issued API key to create and manage your owned links.
        The key stays in this tab’s memory and is cleared on reload. Changing
        access clears the current form and results.
      </p>
      <form aria-label="API access" onSubmit={submit} noValidate>
        <Input
          label="Management API key"
          type="password"
          value={draft}
          autoComplete="off"
          spellCheck={false}
          onChange={(event) => {
            setDraft(event.target.value);
            setError("");
          }}
          error={error}
          hint={
            connected
              ? "A key is set. Enter a replacement to change owners or use a rotated key."
              : "Ask your operator for a key. Keys are not created by this form."
          }
        />
        <div className="result-actions">
          <Button type="submit">Use API key</Button>
          {connected && (
            <Button
              variant="secondary"
              onClick={() => {
                setDraft("");
                setError("");
                onChange("v2", "");
              }}
            >
              Clear API key
            </Button>
          )}
          {mode === "v2" && (
            <Button
              variant="secondary"
              onClick={() => {
                setDraft("");
                setError("");
                onChange("v1", "");
              }}
            >
              Use legacy V1
            </Button>
          )}
        </div>
      </form>
      {mode === "v1" && (
        <p className="muted">
          Legacy V1 is anonymous and deprecated. It cannot manage owned V2
          links.
        </p>
      )}
      {mode === "v2" && (
        <Alert>
          {connected
            ? "V2 requests will use your key. The server checks it on each request."
            : "Enter your API key to continue. No anonymous fallback will be used."}
        </Alert>
      )}
    </details>
  );
}
