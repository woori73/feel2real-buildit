(function (root, factory) {
  var utils = factory();
  if (typeof window !== 'undefined') {
    window.F2RBuilderTextUtils = utils;
  }
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = utils;
  }
})(this, function () {
  'use strict';

  var TEXT_FIELD_KINDS = {
    name: 'singleLine',
    intro: 'multiline'
  };

  var DRAFT_FIELD_KINDS = {
    introTextbookTitle: 'singleLine',
    introTextbookPath: 'singleLine',
    introHeading: 'singleLine',
    introBodySecondary: 'multiline',
    philosophyKeyword: 'singleLine',
    philosophyItem1: 'singleLine',
    philosophyItem2: 'singleLine',
    philosophyItem3: 'singleLine',
    philosophyItem4: 'singleLine',
    monthlyTuition: 'singleLine',
    scoreImprovement: 'singleLine',
    operationYears: 'singleLine'
  };

  function builderTextFieldKind(target) {
    if (!target || typeof target.getAttribute !== 'function') {
      return 'multiline';
    }

    var textKey = target.getAttribute('data-builder-text');
    if (textKey !== null) {
      return TEXT_FIELD_KINDS[textKey] || 'singleLine';
    }

    var draftKey = target.getAttribute('data-builder-draft');
    if (draftKey !== null) {
      return DRAFT_FIELD_KINDS[draftKey] || 'multiline';
    }

    return 'multiline';
  }

  function isSingleLineTextTarget(target) {
    return builderTextFieldKind(target) === 'singleLine';
  }

  function normalizeSingleLineText(value) {
    return String(value == null ? '' : value)
      .replace(/\u00a0/g, ' ')
      .replace(/[\r\n\t]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function normalizeMultilineText(value) {
    return String(value == null ? '' : value)
      .replace(/\r\n?/g, '\n')
      .replace(/\u00a0/g, ' ')
      .trim()
      .split('\n')
      .map(function (line) {
        return line.replace(/[ \t]+/g, ' ').replace(/[ \t]+$/g, '');
      })
      .join('\n')
      .replace(/\n{3,}/g, '\n\n');
  }

  function extractEditableText(target) {
    if (!target) {
      return '';
    }
    if (typeof target.innerText === 'string') {
      return target.innerText;
    }
    if (typeof target.textContent === 'string') {
      return target.textContent;
    }
    return '';
  }

  return {
    builderTextFieldKind: builderTextFieldKind,
    isSingleLineTextTarget: isSingleLineTextTarget,
    normalizeSingleLineText: normalizeSingleLineText,
    normalizeMultilineText: normalizeMultilineText,
    extractEditableText: extractEditableText
  };
});
