// 실제 GrapesJS 0.23.6 출력(projectData/html/css)을 캡처해 서버 정제기 회귀 테스트 fixture로 저장한다.
// 편집기 페이지(academy_editor.html)와 같은 블록/설정을 사용한다. 서버 없이 실행 가능.
//   npm run capture-fixture
const fs = require('fs');
const path = require('path');
const { launch, NM } = require('./lib');

(async () => {
  const b = await launch();
  const page = await b.newPage({ viewport: { width: 1280, height: 900 } });
  await page.setContent('<!doctype html><html><body><div id="gjs"></div></body></html>');
  await page.addScriptTag({ path: NM + '/grapesjs/dist/grapes.min.js' });
  const out = await page.evaluate(() => {
    const editor = grapesjs.init({
      container: '#gjs', fromElement: false, storageManager: false, height: '700px', width: 'auto',
      deviceManager: { devices: [{ name: 'Desktop', width: '' }, { name: 'Tablet', width: '768px', widthMedia: '992px' }, { name: 'Mobile', width: '375px', widthMedia: '768px' }] },
    });
    editor.setComponents(`
      <section data-preset="ENGLISH" style="padding:60px 20px;background:#ecfdf5;text-align:center;">
        <h1 style="font-size:36px;font-weight:800;color:#047857;margin-bottom:16px;">학원 이름</h1>
        <p style="font-size:18px;color:#4b5563;">소개 문구 <b>굵게</b> <a href="https://example.com/a?b=1">링크</a> <a href="/academies/test">내부 링크</a></p>
        <a href="#" class="cta-btn" style="display:inline-block;padding:14px 40px;background:#047857;color:#fff;border-radius:8px;">상담 신청하기</a>
      </section>
      <section style="padding:40px 20px;">
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:20px;">
          <div style="padding:24px;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,.1);"><h3>특징 1</h3><p>설명</p></div>
          <div style="padding:24px;background:#fff;"><h3>특징 2</h3><p>설명</p></div>
        </div>
        <img src="https://via.placeholder.com/800x400?text=img" alt="이미지" style="max-width:100%;height:auto"/>
        <img src="/api/academies/test-english-academy/logo" alt="로고"/>
        <hr/>
        <table><thead><tr><th>구분</th><th>시간</th></tr></thead><tbody><tr><td>월</td><td>19:00</td></tr></tbody></table>
        <ul><li>하나</li><li>둘</li></ul>
      </section>`);
    // 디바이스(모바일) 전용 스타일과 클래스 규칙, 그리고 이미지 에셋(data URI)
    editor.Css.addRules('@media (max-width: 768px){ .cta-btn{display:block;padding:10px;} }');
    editor.Css.addRules('.cta-btn:hover{opacity:.9;}');
    editor.AssetManager.add({ type: 'image', src: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', name: 'dot.png' });
    return { grapesjsVersion: grapesjs.version, projectData: editor.getProjectData(), html: editor.getHtml(), css: editor.getCss() };
  });
  const dir = path.resolve(__dirname, '../../src/test/resources/visual-editor');
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'grapesjs-project.json'), JSON.stringify(out, null, 2) + '\n');
  const comp = out.projectData.pages[0].frames[0].component;
  console.log('GrapesJS', out.grapesjsVersion, '| component keys:', Object.keys(comp).join(','), '| head:', JSON.stringify(comp.head), '| docEl:', JSON.stringify(comp.docEl));
  console.log('html bytes', out.html.length, 'css bytes', out.css.length, 'projectData bytes', JSON.stringify(out.projectData).length);
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
