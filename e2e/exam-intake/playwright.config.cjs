const { defineConfig } = require("@playwright/test");
module.exports = defineConfig({
  testDir: ".",
  testMatch: "*.spec.cjs",
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: "http://localhost:8091",
    timezoneId: "Asia/Seoul",
    browserName: "chromium",
    launchOptions: {
      executablePath: process.env.CHROMIUM_PATH || "/usr/bin/chromium",
      args: ["--no-sandbox"],
    },
    trace: "off",
    screenshot: "only-on-failure",
  },
  reporter: [["list"], ["json", { outputFile: "results.json" }]],
});
