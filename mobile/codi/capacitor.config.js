// The launcher and the WebView allowlist must come from the same build.
const { origin } = require('./.build/target.json');
module.exports = {
  appId: 'kr.co.feel2real.codi',
  appName: 'F2R 코디',
  webDir: '.build/www',
  server: { allowNavigation: [new URL(origin).hostname], errorPath: 'offline.html' }
};
