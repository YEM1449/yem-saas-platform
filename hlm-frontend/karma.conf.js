// Karma configuration
// Defines a ChromeHeadlessCI custom launcher for running tests in CI environments
// (Docker containers, restricted kernel namespaces, ubuntu-24.04 runners, etc.)
// where the Chrome sandbox is unavailable. --no-sandbox is safe in trusted CI.
module.exports = function (config) {
  config.set({
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
      }
    }
  });
};
