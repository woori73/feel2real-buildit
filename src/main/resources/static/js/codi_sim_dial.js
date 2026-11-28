// Hand a plain telephone number to the device. Only the system dialer starts the call.
window.createCodiDialCard = function (message) {
  const card = document.createElement('section');
  card.className = 'ua-draft codi-call-card';
  const title = document.createElement('strong');
  title.textContent = '내 SIM으로 전화';
  const label = document.createElement('label');
  label.textContent = '전화할 번호';
  const number = document.createElement('input');
  number.type = 'tel';
  number.autocomplete = 'off';
  number.placeholder = '전화번호 입력';
  label.append(number);
  const hint = document.createElement('p');
  hint.textContent = '전화 앱에서 통화 버튼을 누르면 선택한 SIM의 요금제가 적용됩니다.';
  const dial = document.createElement('a');
  dial.className = 'ops-btn';
  dial.textContent = '전화 앱 열기';
  function update() {
    const digits = number.value.trim().replace(/[() -]/g, '');
    if (/^\+?\d{2,15}$/.test(digits)) {
      dial.href = 'tel:' + digits;
      dial.removeAttribute('aria-disabled');
    } else {
      dial.removeAttribute('href');
      dial.setAttribute('aria-disabled', 'true');
    }
  }
  // Only prefill an unambiguous number; edits always update the actual dial target.
  const matches = message.match(/(?<![\d+])(?:\+82[ -]?|0)(?:10|2|[3-6][1-5]|70)[ -]?\d{3,4}[ -]?\d{4}(?!\d)/g) || [];
  if (matches.length === 1) number.value = matches[0];
  number.addEventListener('input', update);
  update();
  card.append(title, label, hint, dial);
  return card;
};
