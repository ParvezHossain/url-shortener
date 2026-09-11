import { Component, type ReactNode } from "react";

/** Contains unexpected rendering failures and offers a fresh, safe starting point. */
export class ApiErrorBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  /** Switches to recovery UI without displaying the exception. */
  static getDerivedStateFromError() {
    return { failed: true };
  }

  /** Renders the application or an accessible recovery page. */
  render() {
    if (this.state.failed)
      return (
        <main className="container recovery-page">
          <h1>Something didn’t load correctly.</h1>
          <p>
            Your request won’t be repeated automatically. Start again to reload
            the application.
          </p>
          <a className="button button--primary" href="/">
            Start again
          </a>
        </main>
      );
    return this.props.children;
  }
}
