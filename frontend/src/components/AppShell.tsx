import { useSyncExternalStore } from "react";
import { AnalyticsLookup } from "./AnalyticsLookup";
import { ThemeToggle } from "./ThemeToggle";
import { CreateUrlForm } from "./CreateUrlForm";
import { Card } from "./ui";

function subscribeRoute(callback: () => void) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

/** Provides responsive navigation and a shared content frame for the application. */
export function AppShell() {
  const hash = useSyncExternalStore(subscribeRoute, () => window.location.hash);
  const analytics = hash.split("?")[0] === "#/analytics";
  const code = new URLSearchParams(hash.split("?")[1] ?? "").get("code") ?? "";
  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main")?.focus();
        }}
      >
        Skip to content
      </a>
      <header className="site-header">
        <div className="container header-inner">
          <a className="wordmark" href="/" aria-label="Shortly home">
            <span className="brand-mark" aria-hidden="true">
              ↗
            </span>
            shortly<span className="brand-dot">.</span>
          </a>
          <nav aria-label="Primary navigation">
            <a href="/#overview">Overview</a>
            <a
              href="/#/analytics"
              aria-current={analytics ? "page" : undefined}
            >
              Analytics
            </a>
            <a href="/swagger-ui.html">
              API docs <span aria-hidden="true">↗</span>
            </a>
          </nav>
          <ThemeToggle />
        </div>
      </header>
      <main id="main" tabIndex={-1} className="container">
        {analytics ? (
          <AnalyticsLookup key={code} initialCode={code} />
        ) : (
          <>
            <section className="hero" aria-labelledby="hero-title">
              <div className="hero-copy">
                <span className="eyebrow">
                  <span className="status-dot" /> YOUR LINKS. YOUR SERVER.
                </span>
                <h1 id="hero-title">
                  A little link.
                  <br />
                  <span>A long way.</span>
                </h1>
                <p className="hero-description">
                  Shorten a URL. Make it memorable. Keep every link on your own
                  server.
                </p>
                <p className="hero-note">
                  Self-hosted. Open by design. Built for simplicity.
                </p>
              </div>
              <CreateUrlForm />
            </section>
            <section
              id="overview"
              className="overview"
              aria-labelledby="overview-title"
            >
              <div className="section-heading">
                <div>
                  <p className="eyebrow">LESS FRICTION. MORE CONNECTION.</p>
                  <h2 id="overview-title">
                    The essentials, thoughtfully built.
                  </h2>
                </div>
                <p className="muted">
                  A focused toolkit for the links you share.
                </p>
              </div>
              <div className="feature-grid">
                <Card>
                  <span className="feature-icon" aria-hidden="true">
                    ↗
                  </span>
                  <h3>Make it memorable</h3>
                  <p>
                    Use a generated short code or choose a custom alias that
                    gives your link a little context.
                  </p>
                </Card>
                <Card>
                  <span className="feature-icon" aria-hidden="true">
                    ◷
                  </span>
                  <h3>Set its lifetime</h3>
                  <p>
                    Keep a link around, or choose an expiration time for
                    something that’s only here for a while.
                  </p>
                </Card>
                <Card>
                  <span className="feature-icon" aria-hidden="true">
                    ▥
                  </span>
                  <h3>See the connections</h3>
                  <p>
                    Check total clicks and the latest visit with
                    straightforward, per-link statistics.
                  </p>
                </Card>
              </div>
            </section>
            <aside className="api-banner">
              <div>
                <h2>Your links. Your stack.</h2>
                <p>Connect your tools with a straightforward REST API.</p>
              </div>
              <a className="button button--secondary" href="/swagger-ui.html">
                Read the API docs <span aria-hidden="true">→</span>
              </a>
            </aside>
          </>
        )}
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <span className="wordmark">
            shortly<span className="brand-dot">.</span>
          </span>
          <p>A simpler way to share.</p>
          <a href="/swagger-ui.html">
            API documentation <span aria-hidden="true">↗</span>
          </a>
        </div>
      </footer>
    </div>
  );
}
