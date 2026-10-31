/** Create the same multipart HTML file from an administrator's explicit text input. */
(function () {
  'use strict';
  function fileFor(form) {
    var input = form.querySelector('[name="file"]');
    var file = input && input.files && input.files[0];
    var sourceEl = form.querySelector('[name="htmlSource"]');
    var source = sourceEl ? sourceEl.value : '';
    if (!source.trim()) return file || null;
    if (file) throw new Error('파일 첨부와 HTML 직접 입력 중 하나만 사용해 주세요.');
    if (!/^\s*(?:<!doctype\s+html\b[^>]*>\s*)?<html\b/i.test(source)) {
      throw new Error('HTML 문서 전체를 입력해 주세요. <html> 태그로 시작하는 문서가 필요합니다.');
    }
    var nameEl = form.querySelector('[name="htmlFilename"]');
    var name = nameEl ? nameEl.value.trim() : 'study.html';
    if (!name) name = 'study.html';
    if (/[\\/\x00-\x1f]/.test(name) || !/\.html?$/i.test(name)) {
      throw new Error('파일명은 경로 없이 .html 또는 .htm으로 입력해 주세요.');
    }
    var result = new File([source], name, { type: 'text/html;charset=utf-8' });
    if (result.size > 20 * 1024 * 1024) throw new Error('HTML 파일은 20MB 이하여야 합니다.');
    return result;
  }
  window.WikiHtmlInput = { fileFor: fileFor };
})();
