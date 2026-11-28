// The caller reviews the task and explicitly starts one billable call. Polling only reads its result.
window.createCodiCallCard = function ({ message, academyId, post, get, beginActivity, current }) {
  const form = document.createElement('form');
  form.className = 'ua-draft codi-call-card';
  function add(tag, text) {
    const node = document.createElement(tag);
    if (text) node.textContent = text;
    form.append(node);
    return node;
  }
  add('strong', '코디 AI 전화');
  function field(title, tag) {
    const label = add('label', title);
    const input = document.createElement(tag);
    label.append(input);
    input.required = true;
    return input;
  }
  const number = field('수신 번호', 'input');
  number.type = 'tel';
  number.placeholder = '시험에 등록한 국내 번호';
  const task = field('전달할 내용 또는 확인할 일', 'textarea');
  task.maxLength = 500;
  const detected = message.match(/(?:\+82[ -]?|0)(?:10|2|[3-6][1-5]|70)[ -]?\d{3,4}[ -]?\d{4}/);
  number.value = detected?.[0] || '';
  task.value = message.replace(detected?.[0] || '', '').trim().slice(0, 500);
  const status = add('p', '전화망과 음성 AI 이용료가 발생합니다.');
  status.setAttribute('role', 'status');
  const dial = add('button', '확인하고 전화 걸기');
  dial.type = 'submit';
  const refresh = add('button', '통화 결과 확인');
  refresh.type = 'button';
  refresh.hidden = true;
  const id = crypto.randomUUID();
  let activity, timer;
  const labels = {
    initiated: '발신 요청을 접수했습니다.', 'in-progress': '코디가 통화 중입니다.',
    processing: '통화 결과를 정리하고 있습니다.', done: '통화가 종료되었습니다.',
    failed: '통화가 성사되지 않았습니다.', unconfirmed: '발신 여부를 아직 확인할 수 없습니다.'
  };
  function active() { return current() && form.isConnected; }
  function render(result) {
    if (!active()) return;
    status.textContent = [labels[result.status] || '통화 상태 확인이 필요합니다.', result.summary].filter(Boolean).join(' ');
    const finished = ['done', 'failed'].includes(result.status);
    activity(result.status === 'in-progress' ? 'phone' : finished ? (result.status === 'done' ? 'result' : 'error') : 'work');
    refresh.hidden = finished;
    if (['initiated', 'in-progress', 'processing'].includes(result.status)) timer = setTimeout(poll, 5000);
  }
  async function poll() {
    clearTimeout(timer);
    if (!active() || refresh.disabled) return;
    refresh.disabled = true;
    try { render(await get('/calls/' + id + '?academyId=' + academyId)); }
    catch { if (active()) status.textContent = '통화 상태를 확인하지 못했습니다. 결과를 다시 조회해 주세요.'; }
    finally { refresh.disabled = false; }
  }
  refresh.addEventListener('click', poll);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!active() || dial.disabled) return;
    dial.disabled = number.disabled = task.disabled = true;
    activity = beginActivity('work', 'call');
    status.textContent = '발신을 요청하고 있습니다.';
    try { render(await post('/calls', { requestId: id, academyId, approved: true, number: number.value, task: task.value })); }
    catch (e) {
      if (!active()) return;
      activity('error');
      if ([400, 401, 403, 503].includes(e.status)) {
        status.textContent = e.status === 503 ? '이 환경에는 AI 전화 회선이 연결되어 있지 않습니다.' : e.message;
        dial.disabled = number.disabled = task.disabled = false;
      } else {
        status.textContent = '발신 여부가 불확실합니다. 통화 결과를 조회해 주세요.';
        refresh.hidden = false;
      }
    }
  });
  return form;
};
