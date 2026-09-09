const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const dir = path.resolve(__dirname, '../../main/resources/static/js');
const filter = fs.readFileSync(path.join(dir, 'eduholic_actual_class_video_filter.js'), 'utf8');
const hero = fs.readFileSync(path.join(dir, 'eduholic_midterm_home_live.js'), 'utf8');
const marker = '내신대비_라이브_최종_53초';

test('Hero renderer and Actual Class guard use the same dedicated asset marker', () => {
  assert.ok(filter.includes(marker));
  assert.ok(hero.includes(marker));
});
