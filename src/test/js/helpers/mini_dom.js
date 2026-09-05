'use strict';

/**
 * 아주 작은 실제 DOM 트리 구현. vm 샌드박스의 "getElementById만 흉내내는" 스텁 대신, HTML 문자열을
 * 실제로 파싱해 부모/자식 관계·closest·querySelector·이벤트 버블링까지 진짜로 동작하게 한다.
 *
 * 왜 필요한가: academy_builder.js의 버튼 부착 로직(appendAiAssistControl 등)은 실제 클릭이
 * DOM을 타고 올라가며 이벤트 위임 리스너에 도달해야 실행된다. 테스트가 그 경로를 우회해서
 * 내부 함수를 직접 호출하면("우회 DOM") 렌더 마크업이 실제로 그 클릭을 받아낼 수 있는지는
 * 검증하지 못한다 — 이번 회귀가 바로 그 사각지대에서 나왔다.
 *
 * 지원 셀렉터: 태그, #id, .class, [attr], [attr="val"], 위 조합, 쉼표로 여러 개 나열.
 * 완전한 HTML5 파서가 아니다 — academy_sections.js가 만드는, 항상 정상적으로 닫히는
 * 마크업만 대상으로 한다.
 */

const VOID_TAGS = new Set(['img', 'br', 'hr', 'input', 'meta', 'link']);

function parseAttributes(raw) {
  const attrs = new Map();
  const re = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\s*=\s*("([^"]*)"|'([^']*)'|[^\s"'>]+))?/g;
  let m;
  while ((m = re.exec(raw))) {
    const name = m[1];
    const value = m[3] !== undefined ? m[3] : m[4] !== undefined ? m[4] : (m[2] !== undefined ? m[2] : '');
    attrs.set(name, value);
  }
  return attrs;
}

class MiniElement {
  constructor(tagName, attrs) {
    this.tagName = (tagName || '').toUpperCase();
    this._attrs = attrs || new Map();
    this.children = [];
    this.parentElement = null;
    this._listeners = new Map();
    this._textParts = [];
    const self = this;
    this.classList = {
      add(...names) { names.forEach((n) => self._classSet().add(n)); },
      remove(...names) { names.forEach((n) => self._classSet().delete(n)); },
      toggle(name, force) {
        const set = self._classSet();
        const has = set.has(name);
        const next = force === undefined ? !has : force;
        if (next) set.add(name); else set.delete(name);
        return next;
      },
      contains(name) { return self._classSet().has(name); }
    };
    this.dataset = new Proxy({}, {
      get(_, prop) { return self.getAttribute('data-' + camelToKebab(String(prop))); },
      set(_, prop, value) { self.setAttribute('data-' + camelToKebab(String(prop)), String(value)); return true; }
    });
    this.style = {};
  }

  _classSet() {
    const raw = this._attrs.get('class') || '';
    const set = new Set(raw.split(/\s+/).filter(Boolean));
    const attrs = this._attrs;
    return {
      add: (n) => { set.add(n); attrs.set('class', Array.from(set).join(' ')); },
      delete: (n) => { set.delete(n); attrs.set('class', Array.from(set).join(' ')); },
      has: (n) => set.has(n)
    };
  }

  getAttribute(name) {
    return this._attrs.has(name) ? this._attrs.get(name) : null;
  }

  setAttribute(name, value) {
    this._attrs.set(name, String(value));
  }

  hasAttribute(name) {
    return this._attrs.has(name);
  }

  appendChild(child) {
    child.parentElement = this;
    this.children.push(child);
    return child;
  }

  get id() { return this.getAttribute('id') || ''; }
  get className() { return this._attrs.get('class') || ''; }
  set className(value) { this._attrs.set('class', value); }

  set innerHTML(html) {
    this.children = [];
    this._textParts = [];
    const frag = parseHtml(html);
    frag._textParts.forEach((t) => this._textParts.push(t));
    frag.children.forEach((c) => this.appendChild(c));
  }
  get innerHTML() { return '[innerHTML not serialized in mini_dom]'; }

  addEventListener(type, handler) {
    if (!this._listeners.has(type)) this._listeners.set(type, []);
    this._listeners.get(type).push(handler);
  }

  focus() {}

  remove() {
    if (this.parentElement) {
      const idx = this.parentElement.children.indexOf(this);
      if (idx !== -1) this.parentElement.children.splice(idx, 1);
      this.parentElement = null;
    }
  }

  querySelectorAll(selector) {
    const matchers = selector.split(',').map((s) => compileSelector(s.trim()));
    const out = [];
    walk(this, (node) => {
      if (node === this) return;
      if (matchers.some((m) => m(node))) out.push(node);
    });
    return out;
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  closest(selector) {
    const matchers = selector.split(',').map((s) => compileSelector(s.trim()));
    let node = this;
    while (node) {
      if (matchers.some((m) => m(node))) return node;
      node = node.parentElement;
    }
    return null;
  }

  contains(other) {
    let node = other;
    while (node) {
      if (node === this) return true;
      node = node.parentElement;
    }
    return false;
  }

  get textContent() {
    let out = '';
    walkText(this, (t) => { out += t; });
    return out;
  }
}

function walkText(node, cb) {
  node._textParts.forEach(cb);
  node.children.forEach((c) => walkText(c, cb));
}

function walk(node, cb) {
  node.children.forEach((child) => {
    cb(child);
    walk(child, cb);
  });
}

function camelToKebab(value) {
  return value.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase());
}

/** `tag#id.class1.class2[attr][attr2="val"]` 형태 하나를 매처 함수로 컴파일한다. */
function compileSelector(selector) {
  const parts = [];
  const re = /(^[a-zA-Z][a-zA-Z0-9-]*)|(#[-\w]+)|(\.[-\w]+)|(\[[^\]]+\])/g;
  let m;
  while ((m = re.exec(selector))) {
    if (m[1]) parts.push({ type: 'tag', value: m[1].toUpperCase() });
    else if (m[2]) parts.push({ type: 'id', value: m[2].slice(1) });
    else if (m[3]) parts.push({ type: 'class', value: m[3].slice(1) });
    else if (m[4]) {
      const inner = m[4].slice(1, -1);
      const eq = inner.match(/^([-\w]+)\s*=\s*"?([^"]*)"?$/);
      if (eq) parts.push({ type: 'attr-eq', name: eq[1], value: eq[2] });
      else parts.push({ type: 'attr', name: inner });
    }
  }
  return (node) => parts.every((p) => {
    if (p.type === 'tag') return node.tagName === p.value;
    if (p.type === 'id') return node.id === p.value;
    if (p.type === 'class') return node.classList.contains(p.value);
    if (p.type === 'attr') return node.hasAttribute(p.name);
    if (p.type === 'attr-eq') return node.getAttribute(p.name) === p.value;
    return false;
  });
}

/** HTML 문자열을 MiniElement 트리로 파싱한다. 여러 최상위 노드를 담는 루트 fragment를 돌려준다. */
function parseHtml(html) {
  const root = new MiniElement('#fragment');
  const stack = [root];
  const tagRe = /<!--[\s\S]*?-->|<\/([a-zA-Z][a-zA-Z0-9-]*)\s*>|<([a-zA-Z][a-zA-Z0-9-]*)((?:\s+[^<>]*?)?)\s*(\/)?>|([^<]+)/g;
  let m;
  while ((m = tagRe.exec(html))) {
    if (m[0].startsWith('<!--')) continue;
    if (m[1]) {
      // closing tag
      const name = m[1].toUpperCase();
      for (let i = stack.length - 1; i > 0; i--) {
        if (stack[i].tagName === name) {
          stack.length = i;
          break;
        }
      }
    } else if (m[2]) {
      const el = new MiniElement(m[2], parseAttributes(m[3] || ''));
      stack[stack.length - 1].appendChild(el);
      const selfClosing = !!m[4] || VOID_TAGS.has(el.tagName.toLowerCase());
      if (!selfClosing) stack.push(el);
    } else if (m[5]) {
      const text = m[5];
      if (text.trim()) stack[stack.length - 1]._textParts.push(text);
    }
  }
  return root;
}

/**
 * 실제 브라우저의 버블 단계 클릭 디스패치를 흉내낸다. target에서 시작해 조상으로
 * 올라가며 각 노드에 등록된 'click' 리스너를 순서대로 실행하고, stopPropagation()이
 * 호출되면 더 올라가지 않는다. 캡처 단계는 다루지 않는다(이 프로젝트의 관련 코드가
 * 캡처 리스너에 의존하지 않는다).
 */
function dispatchClick(target, root) {
  const event = {
    target,
    currentTarget: null,
    defaultPrevented: false,
    _propagationStopped: false,
    preventDefault() { this.defaultPrevented = true; },
    stopPropagation() { this._propagationStopped = true; }
  };
  let node = target;
  while (node) {
    const listeners = node._listeners && node._listeners.get('click');
    if (listeners) {
      event.currentTarget = node;
      for (const listener of listeners.slice()) {
        listener(event);
        if (event._propagationStopped) return event;
      }
    }
    if (node === root) break;
    node = node.parentElement;
  }
  return event;
}

module.exports = { parseHtml, MiniElement, dispatchClick };
