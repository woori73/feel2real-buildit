const fs = require('node:fs');
const path = require('node:path');

function resolveOrigin(value = 'https://feel2real.co.kr') {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password || url.href !== `${url.origin}/`) {
    throw new Error('F2R_CODI_ORIGIN must be an HTTPS origin without credentials, a path, a query or a fragment.');
  }
  return url.origin;
}

function build(root, value) {
  const origin = resolveOrigin(value);
  const buildDir = path.join(root, '.build');
  const webDir = path.join(buildDir, 'www');
  fs.mkdirSync(webDir, { recursive: true });
  fs.cpSync(path.join(root, 'www'), webDir, { recursive: true });
  const entry = `${origin}/dashboard/assistant`;
  fs.writeFileSync(path.join(webDir, 'entry.js'), `const codiEntry = ${JSON.stringify(entry)};\n`);
  fs.writeFileSync(path.join(buildDir, 'target.json'), JSON.stringify({ origin }) + '\n');
  return entry;
}

if (require.main === module) console.log(build(path.resolve(__dirname, '..'), process.env.F2R_CODI_ORIGIN));
module.exports = { build, resolveOrigin };
