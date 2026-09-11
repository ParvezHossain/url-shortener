import {
  useEffect,
  useId,
  useRef,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "danger";
  loading?: boolean;
};
/** Provides native keyboard activation and a consistent busy/disabled state. */
export function Button({
  variant = "primary",
  loading = false,
  disabled,
  children,
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      type={type}
      className={`button button--${variant} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <Spinner label="Working" />}
      {children}
    </button>
  );
}
/** Associates a visible label, optional hint, and validation error with its field. */
export function Input({
  label,
  hint,
  error,
  prefix,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
  prefix?: string;
}) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const description = [
    props["aria-describedby"],
    prefix && `${fieldId}-prefix`,
    hint && `${fieldId}-hint`,
    error && `${fieldId}-error`,
  ]
    .filter(Boolean)
    .join(" ");
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <div className={prefix ? "input-with-prefix" : undefined}>
        {prefix && (
          <span id={`${fieldId}-prefix`} className="input-prefix">
            {prefix}
          </span>
        )}
        <input
          {...props}
          id={fieldId}
          aria-invalid={error ? true : props["aria-invalid"]}
          aria-describedby={description || undefined}
        />
      </div>
      {hint && (
        <p id={`${fieldId}-hint`} className="muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${fieldId}-error`} className="error-text">
          {error}
        </p>
      )}
    </div>
  );
}
/** Groups related content within an elevated surface. */
export function Card({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={`card ${className}`} />;
}
/** Announces contextual feedback with urgency appropriate to its severity. */
export function Alert({
  children,
  tone = "info",
}: {
  children: ReactNode;
  tone?: "info" | "error" | "success";
}) {
  return (
    <div
      className={`alert alert--${tone}`}
      role={tone === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
/** Supplies an accessible label while indicating ongoing work. */
export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <span role="status" className="spinner">
      <span className="sr-only">{label}</span>
    </span>
  );
}
/** Reserves space for loading content without adding noise to the accessibility tree. */
export function Skeleton({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div {...props} className={`skeleton ${className}`} aria-hidden="true" />
  );
}
/** Uses a native modal dialog for focus containment, Escape dismissal, and focus restoration. */
export function Modal({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current!;
    const trigger =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
    return () => {
      if (dialog.open) {
        dialog.close();
        if (trigger?.isConnected) trigger.focus();
      }
    };
  }, [open]);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onKeyDown={(event) => {
        if (event.key !== "Tab") return;
        const items = Array.from(
          ref.current!.querySelectorAll<HTMLElement>(
            'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
          ),
        ).filter((item) => item.getClientRects().length > 0);
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <div className="modal-heading">
        <h2 id={titleId}>{title}</h2>
        <Button variant="secondary" aria-label="Close dialog" onClick={onClose}>
          ×
        </Button>
      </div>
      {children}
    </dialog>
  );
}
/** Keeps transient feedback available until explicitly dismissed. */
export function Toast({
  message,
  onDismiss,
}: {
  message: string;
  onDismiss: () => void;
}) {
  return (
    <div className="toast">
      <span role="status">{message}</span>
      <Button
        variant="secondary"
        aria-label="Dismiss notification"
        onClick={onDismiss}
      >
        ×
      </Button>
    </div>
  );
}
