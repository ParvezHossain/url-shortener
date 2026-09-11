import { ThemeToggle } from "./ThemeToggle";
import { Card } from "./ui";

/** Provides responsive navigation and a shared content frame for the application. */
export function AppShell() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="container header-inner">
          <a className="wordmark" href="#main" aria-label="Shortly home">
            <span className="brand-mark" aria-hidden="true">
              ↗
            </span>
            shortly<span className="brand-dot">.</span>
          </a>
          <nav aria-label="Primary navigation">
            <a href="#overview">Overview</a>
            <a href="/swagger-ui.html">
              API docs <span aria-hidden="true">↗</span>
            </a>
          </nav>
          <ThemeToggle />
        </div>
      </header>
      <main id="main" tabIndex={-1} className="container">
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-copy">
            <span className="eyebrow">
              <span className="status-dot" /> SIMPLE LINKS. YOUR INFRASTRUCTURE.
            </span>
            <h1 id="hero-title">
              A little link.
              <br />
              <span>A long way.</span>
            </h1>
            <p className="hero-description">
              Make every connection a little simpler. Shorter URLs, memorable
              aliases, and clear click counts — all on your own server.
            </p>
            <a className="button button--primary" href="/swagger-ui.html">
              Explore the API <span aria-hidden="true">↗</span>
            </a>
            <p className="hero-note">
              Self-hosted. Open by design. Built for simplicity.
            </p>
          </div>
          <Card className="link-preview">
            <div className="preview-heading">
              <span className="eyebrow">SMALL LINK. BIG POSSIBILITIES.</span>
              <span aria-hidden="true">↗</span>
            </div>
            <div className="preview-route">
              <span className="muted">FROM SOMETHING LONG</span>
              <div className="long-url">
                example.com/ideas/a-better-way-to-share
              </div>
            </div>
            <div className="connection-line" aria-hidden="true">
              ↓
            </div>
            <div className="preview-result">
              <span className="muted">TO SOMETHING MEMORABLE</span>
              <strong>
                /your-next-idea <span aria-hidden="true">↗</span>
              </strong>
            </div>
            <div className="preview-footer">
              <span className="preview-badge">Custom alias</span>
              <span className="muted">Illustrative link</span>
            </div>
          </Card>
        </section>
        <section
          id="overview"
          className="overview"
          aria-labelledby="overview-title"
        >
          <div className="section-heading">
            <div>
              <p className="eyebrow">LESS FRICTION. MORE CONNECTION.</p>
              <h2 id="overview-title">The essentials, thoughtfully built.</h2>
            </div>
            <p className="muted">A focused toolkit for the links you share.</p>
          </div>
          <div className="feature-grid">
            <Card>
              <span className="feature-icon" aria-hidden="true">
                ↗
              </span>
              <h3>Make it memorable</h3>
              <p>
                Use a generated short code or choose a custom alias that gives
                your link a little context.
              </p>
            </Card>
            <Card>
              <span className="feature-icon" aria-hidden="true">
                ◷
              </span>
              <h3>Set its lifetime</h3>
              <p>
                Keep a link around, or choose an expiration time for something
                that’s only here for a while.
              </p>
            </Card>
            <Card>
              <span className="feature-icon" aria-hidden="true">
                ▥
              </span>
              <h3>See the connections</h3>
              <p>
                Check total clicks and the latest visit with straightforward,
                per-link statistics.
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
