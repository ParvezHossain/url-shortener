const base = process.env.E2E_BASE_URL || "http://127.0.0.1:8080";
module.exports = {
  ci: {
    collect: {
      url: [base + "/", base + "/#/analytics"],
      numberOfRuns: 3,
      settings: {
        chromeFlags: "--no-sandbox",
        onlyCategories: [
          "performance",
          "accessibility",
          "best-practices",
          "seo",
        ],
      },
    },
    assert: {
      assertions: {
        "categories:performance": [
          "error",
          { minScore: 0.85, aggregationMethod: "pessimistic" },
        ],
        "categories:accessibility": [
          "error",
          { minScore: 0.95, aggregationMethod: "pessimistic" },
        ],
        "categories:best-practices": [
          "error",
          { minScore: 0.95, aggregationMethod: "pessimistic" },
        ],
        "categories:seo": [
          "error",
          { minScore: 0.9, aggregationMethod: "pessimistic" },
        ],
      },
    },
    upload: { target: "filesystem", outputDir: "./lighthouse-report" },
  },
};
