import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";

beforeEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

// jsdom has no native dialog implementation; browser focus behavior is checked separately.
Object.defineProperties(HTMLDialogElement.prototype, {
  showModal: {
    configurable: true,
    writable: true,
    value() {
      this.open = true;
    },
  },
  close: {
    configurable: true,
    writable: true,
    value() {
      this.open = false;
    },
  },
});
