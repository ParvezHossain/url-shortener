import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: { sourcemap: false },
  server: {
    proxy: {
      "/ui": process.env.API_PROXY_TARGET || "http://localhost:8080",
      "/api": process.env.API_PROXY_TARGET || "http://localhost:8080",
      "/swagger-ui": process.env.API_PROXY_TARGET || "http://localhost:8080",
      "/v3/api-docs": process.env.API_PROXY_TARGET || "http://localhost:8080",
    },
  },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    restoreMocks: true,
  },
});
