const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const ROOT = path.resolve(__dirname, '../..');
const template = fs.readFileSync(path.join(ROOT, 'main/resources/templates/tutor/academy_builder.html'), 'utf8');
const guideCss = fs.readFileSync(path.join(ROOT, 'main/resources/static/css/academy_builder_ai_guide.css'), 'utf8');
const builderJs = fs.readFileSync(path.join(ROOT, 'main/resources/static/js/academy_builder.js'), 'utf8');

test('Bildi launcher와 panel은 canvas-frame 밖에 있어 Preview overlay를 만들지 않는다', () => {
  const canvasStart = template.indexOf('<div class="canvas-frame" id="canvasFrame">');
  const canvasEnd = template.indexOf('</div><!-- /canvas-frame -->', canvasStart);
  const canvasMarkup = template.slice(canvasStart, canvasEnd);
  assert.doesNotMatch(canvasMarkup, /builderBildiStage|builderAiGuidePanel|bildi-bubble/);
  assert.match(template, /id="builderBildiLauncher"/);
  assert.match(template, /id="builderAiGuidePanel"/);
});

test('desktop dock, tablet drawer, mobile bottom sheet breakpoints가 명시돼 있다', () => {
  assert.match(guideCss, /@media \(min-width:\s*1281px\)/);
  assert.match(guideCss, /padding-right:\s*calc\(clamp\(300px,\s*22vw,\s*360px\) \+ 24px\)/);
  assert.match(guideCss, /@media \(min-width:\s*721px\) and \(max-width:\s*1280px\)/);
  assert.match(guideCss, /width:\s*min\(360px,\s*42vw\)/);
  assert.match(guideCss, /@media \(max-width:\s*720px\)/);
  assert.match(guideCss, /height:\s*min\(62dvh,\s*520px\)/);
  assert.match(guideCss, /transform:\s*translateY\(100%\)/);
});

test('enabled/panelOpen은 Guide session과 별도 저장되며 OFF가 session을 지우지 않는다', () => {
  assert.match(builderJs, /let aiGuideEnabled = true/);
  assert.match(builderJs, /let aiGuidePanelOpen = false/);
  assert.match(builderJs, /enabled:\s*!!aiGuideEnabled/);
  assert.match(builderJs, /panelOpen:\s*!!aiGuidePanelOpen/);
  const fn = builderJs.slice(builderJs.indexOf('function setAiGuideEnabled'), builderJs.indexOf('function setAiGuidePanelOpen'));
  assert.doesNotMatch(fn, /aiGuideSession\s*=|removeItem\(/);
});
