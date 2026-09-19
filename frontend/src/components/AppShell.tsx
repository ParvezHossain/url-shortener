import { ApiAccess } from "./ApiAccess";
import { OwnerLinks } from "./OwnerLinks";
import { useState, useSyncExternalStore } from "react";
import { AnalyticsLookup } from "./AnalyticsLookup";
import { ThemeToggle } from "./ThemeToggle";
import { CreateUrlForm } from "./CreateUrlForm";
import { QrCodePanel } from "./QrCodePanel";
import { Card } from "./ui";

function subscribeRoute(callback: () => void) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

/** Provides responsive navigation and a shared content frame for the application. */
export function AppShell() {
  const hash = useSyncExternalStore(subscribeRoute, () => window.location.hash);
  const [access, setAccess] = useState<{
    mode: "v1" | "v2";
    apiKey: string;
    revision: number;
  }>(() => ({
    mode:
      window.location.hash.includes("mode=v2") ||
      window.location.hash.startsWith("#/links")
        ? "v2"
        : "v1",
    apiKey: "",
    revision: 0,
  }));
  const links = hash.split("?")[0] === "#/links";
  const requiresKey =
    links || new URLSearchParams(hash.split("?")[1] ?? "").get("mode") === "v2";
  const mode = requiresKey ? "v2" : access.mode;
  const apiKey = mode === "v2" ? access.apiKey : "";
  const qr = hash.split("?")[0] === "#/qr";
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
          <a className="wordmark" href="/#overview" aria-label="Shortly home">
            <span className="brand-mark" aria-hidden="true">
              ↗
            </span>
            shortly<span className="brand-dot">.</span>
          </a>
          <nav aria-label="Primary navigation">
            <a
              href="/#overview"
              aria-current={!analytics && !qr && !links ? "page" : undefined}
            >
              Overview
            </a>
            <a
              href="/#/analytics"
              aria-current={analytics ? "page" : undefined}
            >
              Analytics
            </a>
            <a href="/#/qr" aria-current={qr ? "page" : undefined}>
              QR codes
            </a>
            {mode === "v2" && (
              <a
                href="/#/links?mode=v2"
                aria-current={links ? "page" : undefined}
              >
                My links
              </a>
            )}
            <a href="/swagger-ui.html">
              API docs <span aria-hidden="true">↗</span>
            </a>
          </nav>
          <ThemeToggle />
        </div>
      </header>
      <main id="main" tabIndex={-1} className="container">
        <ApiAccess
          mode={mode}
          connected={!!apiKey}
          onChange={(nextMode, nextKey) => {
            setAccess((previous) => ({
              mode: nextMode,
              apiKey: nextKey,
              revision: previous.revision + 1,
            }));
            if (nextMode === "v1") window.location.hash = "#overview";
          }}
        />
        <div key={access.revision}>
          {mode === "v2" && !apiKey ? (
            <section className="analytics-page">
              <h1>API key required</h1>
              <p>
                Open API access above to enter your key and manage owned links.
              </p>
            </section>
          ) : links ? (
            <OwnerLinks apiKey={apiKey} />
          ) : qr ? (
            <QrCodePanel apiKey={apiKey} />
          ) : analytics ? (
            <AnalyticsLookup key={code} initialCode={code} apiKey={apiKey} />
          ) : (
            <>
              <section className="hero" aria-labelledby="hero-title">
                <div className="light-trail" aria-hidden="true" />
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
                    Shorten a URL. Make it memorable. Keep every link on your
                    own server.
                  </p>
                  <p className="hero-note">
                    Self-hosted. Open by design. Built for simplicity.
                  </p>
                </div>
                <CreateUrlForm apiKey={apiKey} />
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
                <p className="feature-caption">Features at a glance</p>
                <div className="feature-grid">
                  <Card>
                    <span className="feature-icon" aria-hidden="true">
                      ↗
                    </span>
                    <h3>Make it memorable</h3>
                    <div
                      className="feature-visual alias-visual"
                      aria-hidden="true"
                    >
                      <span>Custom alias</span>
                      <span>
                        your-next-idea <b>↗</b>
                      </span>
                    </div>
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
                    <div
                      className="feature-visual time-visual"
                      aria-hidden="true"
                    >
                      <span>ON YOUR SCHEDULE</span>
                      <strong>00:00:00</strong>
                      <i />
                    </div>
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
                    <div
                      className="feature-visual chart-visual"
                      aria-hidden="true"
                    >
                      <svg viewBox="0 0 200 48" fill="none">
                        <path d="M0 38L22 29L44 34L66 13L88 21L110 9L132 18L154 5L176 14L200 2" />
                        <path d="M0 44L22 38L44 42L66 27L88 32L110 23L132 30L154 19L176 27L200 14" />
                      </svg>
                      <span>Every connection counts</span>
                    </div>
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
        </div>
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
