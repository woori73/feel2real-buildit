/*
 * NewStart Chrome Agent — FSS STEP 1~4 detector (Phase 2 PoC).
 *
 * 원칙:
 *  - STRUCTURAL DOM만 사용한다: URL path, title, heading, 버튼/링크 텍스트,
 *    radio의 name/value 속성(선택지 정의), hidden input의 name 속성, 알려진 id의 존재.
 *  - 사용자가 입력한 값(input.value), textarea, 비밀번호, 인증 관련 것은 절대 읽지 않는다.
 *    (radio/hidden의 value는 "선택지 정의" 속성이며 사용자 입력이 아니다. 텍스트 계열
 *     input은 name만 수집하고 value 속성조차 읽지 않는다.)
 *  - selector/text는 2026-09-24 HOME POC + ACADEMY 실측 evidence에 없는 것을 만들지 않는다.
 *    (handoff §3, NEWSTART_FSS_AGENT_HANDOFF_2026-09-24_HOME_TO_ACADEMY.md §10)
 *  - 확신 없으면 UNKNOWN. (§9)
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.NewStartFssDetector = factory();
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  var VERSION = '0.3.0';

  function trimText(s, max) {
    var t = (s || '').replace(/\s+/g, ' ').trim();
    return t.length > max ? t.slice(0, max) : t;
  }

  /* content script가 실제 document로부터 만드는 구조 사실 팩. 값 계열은 여기서 차단. */
  function collectFacts(doc, href) {
    var facts = {
      version: VERSION,
      path: '',
      title: '',
      headings: [],
      buttons: [],
      labels: [],         /* label 요소 텍스트(선택지/영역 명칭) — 입력값 아님 */
      radioOptions: {},   /* { name: [value,...] } — 선택지 정의만 */
      hiddenNames: [],    /* hidden input의 name만 (value 미조회) */
      progressLabels: [], /* 사이드 진행 단계 텍스트 "N단계 …" (2026-09-24 실측 구조) */
      knownIds: {}        /* evidence로 알려진 id의 존재 여부만 */
    };
    try {
      var a = doc.createElement('a');
      a.href = href;
      facts.path = (a.pathname || '').toLowerCase();
    } catch (e) { /* noop */ }
    facts.title = trimText(doc.title, 60);

    var hs = doc.querySelectorAll('h1,h2,h3,h4,legend,caption');
    for (var i = 0; i < hs.length && facts.headings.length < 30; i++) {
      var ht = trimText(hs[i].textContent, 30);
      if (ht && facts.headings.indexOf(ht) < 0) facts.headings.push(ht);
    }

    var bs = doc.querySelectorAll('a,button,input[type=button],input[type=submit]');
    for (var j = 0; j < bs.length && facts.buttons.length < 60; j++) {
      var bt = trimText(bs[j].value || bs[j].textContent || bs[j].getAttribute('title') || '', 16);
      if (bt && facts.buttons.indexOf(bt) < 0) facts.buttons.push(bt);
    }

    var rs = doc.querySelectorAll('input[type=radio]');
    for (var k = 0; k < rs.length; k++) {
      var nm = rs[k].name;
      if (!nm || nm.length > 40) continue;
      var vals = facts.radioOptions[nm] || (facts.radioOptions[nm] = []);
      var vv = trimText(rs[k].getAttribute('value') || '', 12);
      if (vv && vals.indexOf(vv) < 0 && vals.length < 6) vals.push(vv);
    }

    var hid = doc.querySelectorAll('input[type=hidden]');
    for (var m = 0; m < hid.length && facts.hiddenNames.length < 40; m++) {
      var hn = hid[m].name;
      if (hn && hn.length <= 40 && facts.hiddenNames.indexOf(hn) < 0) facts.hiddenNames.push(hn);
    }

    /* 라벨 텍스트(선택지 설명 문구) — 구조적 라벨이지 사용자 입력값이 아니다. */
    var lb = doc.querySelectorAll('label');
    for (var n2 = 0; n2 < lb.length && facts.labels.length < 40; n2++) {
      var lt = trimText(lb[n2].textContent, 30);
      if (lt && facts.labels.indexOf(lt) < 0) facts.labels.push(lt);
    }

    /* #popup-alert: HOME POC STEP4 실측(재설정/민원신청/닫기 모달). 존재 여부만 읽는다. */
    var pa = doc.getElementById('popup-alert');
    facts.knownIds['popup-alert'] = !!pa;
    /* 실측(2026-09-24): 좌측 진행 단계가 li/label/a/span에 "1단계 개인정보동의" 형태로 존재.
       FSS 자체 4단계 모델(동의/입력/민원정보/등록) — 텍스트만 수집, 값 미열람. */
    var pn = doc.querySelectorAll('li,label,a,span,em,strong');
    for (var q = 0; q < pn.length && facts.progressLabels.length < 12; q++) {
      var pt = trimText(pn[q].textContent, 24);
      if (/^[1-4]\s*단계/.test(pt) && facts.progressLabels.indexOf(pt) < 0) facts.progressLabels.push(pt);
    }
    return facts;
  }

  function has(list, re) {
    for (var i = 0; i < list.length; i++) if (re.test(list[i])) return true;
    return false;
  }

  /* FSS 자체 4단계 모델(개인정보동의/입력/민원정보입력/민원등록)을 NewStart 내부 STEP과 분리해
     추출. 2026-09-24 REAL QA 실측: 타이틀 자체가 현재 단계를 명시 —
     "1단계 개인정보동의 | 금융감독원 민원신청"(forInsertAgre.do), "2단계 개인정보입력 | …"(forInsertStep1.do).
     진행 nav에는 1~4단계가 모두 존재하므로 nav 텍스트로 현재 단계를 추정하지 않고 타이틀만 근거로 쓴다. */
  function deriveFssStage(f) {
    var m = /([1-4])\s*단계\s*([^|]{2,20})/.exec(f.title || '');
    if (!m) return null;
    var name = trimText(m[2], 20);
    if (!name) return null;
    return { no: parseInt(m[1], 10), name: name };
  }

  /* 순수 판별. fixture 단위 테스트 대상. */
  function classify(f) {
    var ev = [];
    var path = f.path || '';

    /* STEP 3 — forInsertSelect.do: minwonChk Y/N + [다음](checkForm) (POC+ACADEMY 실측) */
    if (path.indexOf('/forinsertselect.do') >= 0) {
      var chk = f.radioOptions && f.radioOptions.minwonChk;
      if (chk && chk.indexOf('Y') >= 0 && chk.indexOf('N') >= 0 && has(f.buttons, /^다음$/)) {
        ev.push('URL:forInsertSelect.do', 'RADIO:minwonChk(Y,N)', 'BUTTON:다음');
        return { step: 3, evidence: ev };
      }
      return { step: 'UNKNOWN', evidence: ['URL:forInsertSelect.do(부분 일치, 선택지 미확인)'] };
    }

    /* STEP 4 — forInsertAgre.do: 금감원 진행 1단계 "개인정보동의".
       2026-09-24 REAL QA 실측: agreechkYn/sinfoSndYn/agreechkRgnoYn은 hidden이 아니라
       **radio(선택지 Y/N, 화면 표시 "동의"/"동의하지 않음")**로 존재. HOME POC가 본 화면과
       다른 경로(직접 진입)에서는 hidden으로 있을 수 있어 두 형태를 모두 구조 근거로 받되,
       URL 단독으로는 확정하지 않는다. */
    if (path.indexOf('/forinsertagre.do') >= 0) {
      var h = f.hiddenNames || [];
      var ro = f.radioOptions || {};
      function yn(name) {
        if (h.indexOf(name) >= 0) return true;
        var v = ro[name];
        return !!(v && v.indexOf('Y') >= 0 && v.indexOf('N') >= 0);
      }
      var three = yn('agreechkYn') && yn('sinfoSndYn') && yn('agreechkRgnoYn');
      if (three) {
        ev.push('URL:forInsertAgre.do',
          ro.agreechkYn ? 'RADIO:agreechkYn+sinfoSndYn+agreechkRgnoYn(Y/N)' : 'HIDDEN:agreechkYn+sinfoSndYn+agreechkRgnoYn');
        if (has(f.buttons, /^다음$/)) ev.push('BUTTON:다음');
        if (has(f.headings, /제3자\s*제공/) || has(f.labels || [], /동의하지\s*않음/)) ev.push('TEXT:개인정보 제3자 제공/동의');
        var stage4 = deriveFssStage(f);
        if (stage4) ev.push('TITLE:' + stage4.no + '단계 ' + stage4.name);
        if (f.knownIds && f.knownIds['popup-alert']) {
          ev.push('ID:#popup-alert(모달 노출)');
          return { step: 4, substate: 'POPUP_ALERT', fssStage: stage4, evidence: ev };
        }
        return { step: 4, substate: 'AGREE', fssStage: stage4, evidence: ev };
      }
      return { step: 'UNKNOWN', evidence: ['URL:forInsertAgre.do(동의 항목 미확인)'] };
    }

    /* STEP 5~7 — forInsertStep1.do: 금감원 진행 2단계 "개인정보입력(본인인증)" 화면.
       2026-09-24 REAL QA 실측: 타이틀 "2단계 개인정보입력 …", heading "개인정보입력(본인인증)",
       버튼 [실명인증]/[본인인증받기]/[주소 찾기]/[다음 단계], radio receiveYn/rsltReceiveYn/avoidYn.
       *** 인식·안내 전용. 절대 자동 입력·자동 클릭·다음/인증/제출 대행 없음(§12). ***
       입력·인증은 전적으로 사용자 영역이며 화면의 어떤 input 값도 읽지 않는다. */
    if (path.indexOf('/forinsertstep1.do') >= 0) {
      var stage5 = deriveFssStage(f);
      var piiSignals = (stage5 && stage5.no === 2) || has(f.buttons, /본인인증/) || has(f.buttons, /실명인증/)
        || has(f.headings, /개인정보\s*입력/) || Object.keys(f.radioOptions || {}).length > 0;
      if (piiSignals) {
        ev.push('URL:forInsertStep1.do');
        if (stage5) ev.push('TITLE:' + stage5.no + '단계 ' + stage5.name);
        if (has(f.buttons, /본인인증/) || has(f.buttons, /실명인증/)) ev.push('BUTTON:본인/실명인증');
        if (has(f.headings, /개인정보\s*입력/)) ev.push('HEADING:개인정보입력');
        return { step: 5, substate: 'PII_INPUT', fssStage: stage5, evidence: ev };
      }
      /* URL은 맞지만 구조 신호가 약하면 무리하게 확정하지 않고 UNKNOWN으로 정직 처리. */
      return { step: 'UNKNOWN', evidence: ['URL:forInsertStep1.do(구조 신호 미확인)'] };
    }

    /* STEP 1 — search.jsp 진입 화면: [신청](fnOkClick) + 제목 "금융감독원 민원신청" (POC+ACADEMY 실측).
       STEP 2는 [신청] 클릭 시의 네이티브 alert()이라 DOM이 없다 — content script가 관측 불가이므로
       search.jsp에서는 계속 STEP 1으로 판별하고, 안내 문구에 [확인] pop-up 처리를 포함한다(§9 정직 고지). */
    if (path === '/fss/minwon/search.jsp') {
      if (has(f.buttons, /^신청$/) && (f.title.indexOf('민원신청') >= 0 || has(f.headings, /민원신청/))) {
        ev.push('URL:/fss/minwon/search.jsp', 'TITLE:민원신청', 'BUTTON:신청');
        return { step: 1, evidence: ev };
      }
      return { step: 'UNKNOWN', evidence: ['URL:search.jsp([신청]/제목 조건 미충족)'] };
    }

    /* path 표시는 .do 확장자와 실제 경로를 충분히 보여줘야 진단에 유용하다(slice(0,40)이
       '.do'를 잘라내 "매칭 누락"처럼 보이게 한 원인이 되므로 60자로 완화). */
    return { step: 'UNKNOWN', evidence: ['URL 매칭 없음(path=' + path.slice(0, 60) + ')'] };
  }

  /* 패널 안내문 — 실측 evidence에 있는 화면 사실만 쓴다. 금지 문구 없음. */
  var GUIDE = {
    1: '유사사례 검색 화면입니다. 화면의 [신청]을 누르면 안내 창이 뜨고, 그 안에서 [확인]을 누르면 다음 단계로 넘어갑니다.',
    3: '민원 성격을 확인하는 단계입니다. 질문에 [네]/[아니요]를 직접 선택한 뒤 [다음]을 눌러주세요.',
    4: '개인정보 동의 3종(수집·이용 / 민감정보 / 고유식별정보) 단계입니다. [동의]를 선택하고 [다음]을 누른 뒤, 뜨는 창에서 [민원신청]을 눌러주세요.',
    '4-POPUP_ALERT': '창에서 [민원신청]을 누르면 개인정보 입력 화면으로 넘어갑니다. 입력과 인증은 사용자 직접 영역입니다.',
    5: '개인정보 입력 화면(forInsertStep1.do)입니다. 성명·생년월일·연락처·주소는 화면에서 직접 입력하고, [본인인증받기]도 본인이 직접 진행해주세요. NewStart는 입력·인증·제출을 대신하지 않으며 이 화면의 입력값을 읽지 않습니다.',
    UNKNOWN: '현재 화면을 자동으로 확인하지 못했습니다. 수동 안내를 이용해주세요.'
  };

  return { VERSION: VERSION, collectFacts: collectFacts, classify: classify, GUIDE: GUIDE };
});
