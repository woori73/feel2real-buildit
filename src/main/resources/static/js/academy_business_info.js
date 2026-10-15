(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RAcademyBusinessInfo = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const EDITABLE_KEYS = Object.freeze([
    'businessName',
    'representative',
    'address',
    'businessRegistrationNumber',
    'mailOrderRegistrationNumber',
    'phone',
    'email'
  ]);
  const MASKED_DEFAULTS = Object.freeze({
    businessName: '학원명',
    representative: '대표자명',
    address: '00시 00구 00로 00',
    businessRegistrationNumber: '000-00-00000',
    mailOrderRegistrationNumber: '0000-00-0000-0000',
    phone: '000-0000-0000',
    email: 'academy@example.com'
  });
  const FTC_URL_PREFIX = 'https://www.ftc.go.kr/bizCommPop.do?wrkr_no=';
  // 상담 신청 모달의 연락 수단 정의. 순서 = 모달에 노출되는 순서(전화 우선).
  const CONTACT_CHANNEL_DEFS = Object.freeze([
    Object.freeze({ key: 'phone', label: '전화하기', icon: '📞' }),
    Object.freeze({ key: 'email', label: '이메일 문의', icon: '✉️' })
  ]);
  // 연락처 입력 모달의 도메인 선택 목록. 마지막 '직접입력'은 select 값이 아니라 sentinel이다.
  const EMAIL_DOMAIN_PRESETS = Object.freeze([
    'naver.com',
    'gmail.com',
    'daum.net',
    'hanmail.net',
    'nate.com',
    'kakao.com',
    'outlook.com',
    'icloud.com'
  ]);
  const EMAIL_DOMAIN_CUSTOM = '__custom__';
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  function normalizeRaw(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    return EDITABLE_KEYS.reduce((normalized, key) => {
      const value = source[key];
      normalized[key] = value == null ? '' : String(value).trim();
      return normalized;
    }, {});
  }

  // 사업자 상호는 학원명과 다를 수 있다(법인명 vs 학원 브랜드명). 예전에는 상호가 비면
  // 학원명으로 채웠는데, 그러면 푸터가 학원명을 법적 상호로 표기하는 셈이라 폴백을 없앴다.
  // 미입력 필드는 전부 MASKED_DEFAULTS로 떨어진다.
  function resolveDisplay(raw) {
    const normalized = normalizeRaw(raw);
    return EDITABLE_KEYS.reduce((resolved, key) => {
      resolved[key] = normalized[key] || MASKED_DEFAULTS[key];
      return resolved;
    }, {});
  }

  function registrationDigits(value) {
    return String(value || '').replace(/\D/g, '');
  }

  function formatRegistrationNumber(value) {
    const digits = registrationDigits(value).slice(0, 10);
    if (digits.length <= 3) return digits;
    if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
  }

  function deriveFtcUrl(value) {
    const digits = registrationDigits(value);
    return digits.length === 10 ? FTC_URL_PREFIX + digits : '';
  }

  function phoneDigits(value) {
    return String(value || '').replace(/\D/g, '');
  }

  /**
   * 전화번호 자동 포맷. 입력 중에도 부분 결과를 그대로 쓸 수 있게 점진적으로 만든다.
   *
   * 가입자번호 4자리를 뒤에서 고정하고, 남는 앞부분을 국번으로 본다. 서울 `02`만 국번이
   * 한 자리 짧아(2-3-4 / 2-4-4) 시내번호 길이가 다르므로 head를 2로 잡는다.
   * 그 외 지역/이동전화는 3-3-4(10자리) / 3-4-4(11자리)가 된다.
   */
  function formatPhoneNumber(value) {
    const digits = phoneDigits(value).slice(0, 11);
    const head = digits.startsWith('02') ? 2 : 3;
    if (digits.length <= head) return digits;
    const rest = digits.slice(head);
    if (rest.length <= 4) return `${digits.slice(0, head)}-${rest}`;
    return `${digits.slice(0, head)}-${rest.slice(0, -4)}-${rest.slice(-4)}`;
  }

  /** 저장 전 최소 검증. 하이픈 위치가 아니라 자리수만 본다(입력 중 자동 포맷이 형태를 맞춘다). */
  function isValidPhone(value) {
    const digits = phoneDigits(value);
    return digits.length >= 9 && digits.length <= 11 && digits.startsWith('0');
  }

  function isValidEmail(value) {
    return EMAIL_PATTERN.test(String(value || '').trim());
  }

  /** 저장된 이메일을 모달의 local/domain 두 입력으로 되돌린다. */
  function splitEmail(value) {
    const text = String(value || '').trim();
    const at = text.lastIndexOf('@');
    if (at <= 0) return { local: text, domain: '' };
    return { local: text.slice(0, at), domain: text.slice(at + 1) };
  }

  /** 둘 다 비면 빈 문자열(= 연락처 지움). 한쪽만 있으면 검증에서 걸리도록 그대로 합친다. */
  function joinEmail(local, domain) {
    const localPart = String(local || '').trim();
    const domainPart = String(domain || '').trim();
    if (!localPart && !domainPart) return '';
    return `${localPart}@${domainPart}`;
  }

  function rows(raw) {
    const normalized = normalizeRaw(raw);
    const info = resolveDisplay(normalized);
    const registration = normalized.businessRegistrationNumber;
    const hasBlankRegistration = registration === '';
    const ftcUrl = deriveFtcUrl(hasBlankRegistration
      ? MASKED_DEFAULTS.businessRegistrationNumber
      : registration);
    return [
      [
        valueItem('상호명', info.businessName, 'businessName'),
        valueItem('대표', info.representative, 'representative'),
        valueItem('주소', info.address, 'address')
      ],
      [
        valueItem('사업자등록번호', info.businessRegistrationNumber, 'businessRegistrationNumber'),
        valueItem('통신판매업신고번호', info.mailOrderRegistrationNumber, 'mailOrderRegistrationNumber'),
        ftcUrl
          ? linkItem('사업자 정보 확인', ftcUrl)
          : disabledLinkItem('사업자 정보 확인')
      ],
      [
        valueItem('전화', info.phone, 'phone'),
        valueItem('이메일', info.email, 'email')
      ]
    ];
  }

  function valueItem(label, value, key) {
    return { label, value, key };
  }

  function linkItem(label, url) {
    return { label, url, disabled: false };
  }

  function disabledLinkItem(label) {
    return { label, url: '', disabled: true };
  }

  /**
   * 상담 신청 모달에 띄울 연락 수단 목록.
   *
   * footer 렌더(resolveDisplay)와 달리 마스킹 dummy를 절대 쓰지 않는다. 강사가 실제로 입력한
   * 값만 통과시켜야 하며, 그러지 않으면 방문자가 `000-0000-0000`으로 전화를 걸게 된다.
   * 둘 다 비어 있으면 빈 배열 = 모달을 열지 않는다는 신호다.
   */
  function contactChannels(raw) {
    const normalized = normalizeRaw(raw);
    return CONTACT_CHANNEL_DEFS
      .map((def) => ({
        key: def.key,
        label: def.label,
        icon: def.icon,
        value: normalized[def.key],
        href: contactHref(def.key, normalized[def.key])
      }))
      .filter((channel) => channel.href !== '');
  }

  /** 전화는 다이얼 불가 문자를 걷어낸다. 숫자가 하나도 없으면 링크를 만들지 않는다. */
  function contactHref(key, value) {
    if (!value) return '';
    if (key === 'phone') {
      const dialable = value.replace(/[^0-9+]/g, '');
      return /[0-9]/.test(dialable) ? `tel:${dialable}` : '';
    }
    return `mailto:${value}`;
  }

  // 공개 표시 전용. 상호 미입력이면 마스킹 값('학원명')조차 법적 표기로 읽힐 수 있어 항목을 뺀다.
  // 편집 화면은 rows()를 직접 부르므로 입력 자리가 그대로 남는다.
  function renderRows(raw, escapeHtml) {
    const escape = typeof escapeHtml === 'function' ? escapeHtml : defaultEscape;
    const blankBusinessName = normalizeRaw(raw).businessName === '';
    return rows(raw).map((group) => {
      const items = group.filter((item) => !(blankBusinessName && item.key === 'businessName')).map((item) => {
        if (item.url) {
          return `<a class="academy-business-footer__link" href="${escape(item.url)}" target="_blank" rel="noopener">${escape(item.label)}</a>`;
        }
        if (item.disabled) {
          return `<span class="academy-business-footer__link is-disabled" aria-disabled="true">${escape(item.label)}</span>`;
        }
        return `<span class="academy-business-footer__item" data-business-info-field="${escape(item.key)}"><b>${escape(item.label)}:</b> <span>${escape(item.value)}</span></span>`;
      }).join('');
      return `<div class="academy-business-footer__row">${items}</div>`;
    }).join('');
  }

  function defaultEscape(value) {
    return String(value || '').replace(/[&<>'"]/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[character]);
  }

  return {
    EDITABLE_KEYS,
    MASKED_DEFAULTS,
    FTC_URL_PREFIX,
    normalizeRaw,
    resolveDisplay,
    registrationDigits,
    formatRegistrationNumber,
    deriveFtcUrl,
    contactChannels,
    EMAIL_DOMAIN_PRESETS,
    EMAIL_DOMAIN_CUSTOM,
    phoneDigits,
    formatPhoneNumber,
    isValidPhone,
    isValidEmail,
    splitEmail,
    joinEmail,
    rows,
    renderRows
  };
});
