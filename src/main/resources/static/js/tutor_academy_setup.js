(function () {
  const FUTURE_SECTION_IDS = [
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment'
  ];
  const form = document.getElementById('academyForm');
  const statusMsg = document.getElementById('statusMsg');
  const intro = document.getElementById('intro');
  const introCount = document.getElementById('introCount');
  let existingAcademy = null;

  const REGION_OPTIONS = {
    '서울시': ['종로구', '중구', '용산구', '성동구', '광진구', '동대문구', '중랑구', '성북구', '강북구', '도봉구', '노원구', '은평구', '서대문구', '마포구', '양천구', '강서구', '구로구', '금천구', '영등포구', '동작구', '관악구', '서초구', '강남구', '송파구', '강동구'],
    '부산시': ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구', '금정구', '강서구', '연제구', '수영구', '사상구', '기장군'],
    '대구시': ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구', '달성군', '군위군'],
    '인천시': ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구', '강화군', '옹진군'],
    '광주시': ['동구', '서구', '남구', '북구', '광산구'],
    '대전시': ['동구', '중구', '서구', '유성구', '대덕구'],
    '울산시': ['중구', '남구', '동구', '북구', '울주군'],
    '세종시': ['전 지역'],
    '경기도': ['수원시', '성남시', '의정부시', '안양시', '부천시', '광명시', '평택시', '동두천시', '안산시', '고양시', '과천시', '구리시', '남양주시', '오산시', '시흥시', '군포시', '의왕시', '하남시', '용인시', '파주시', '이천시', '안성시', '김포시', '화성시', '광주시', '양주시', '포천시', '여주시'],
    '강원도': ['춘천시', '원주시', '강릉시', '동해시', '태백시', '속초시', '삼척시'],
    '충청북도': ['청주시', '충주시', '제천시'],
    '충청남도': ['천안시', '공주시', '보령시', '아산시', '서산시', '논산시', '계룡시', '당진시'],
    '전라북도': ['전주시', '군산시', '익산시', '정읍시', '남원시', '김제시'],
    '전라남도': ['목포시', '여수시', '순천시', '나주시', '광양시'],
    '경상북도': ['포항시', '경주시', '김천시', '안동시', '구미시', '영주시', '영천시', '상주시', '문경시', '경산시'],
    '경상남도': ['창원시', '진주시', '통영시', '사천시', '김해시', '밀양시', '거제시', '양산시'],
    '제주도': ['제주시', '서귀포시']
  };

  init();

  async function init() {
    initIntroCounter();
    initHandleChecker();
    initRegionSelects();
    await loadSubjects();
    await loadExistingAcademy();
  }

  // handle 입력 → 미리보기 갱신 + 디바운스 후 실시간 중복/형식 체크.
  function initHandleChecker() {
    const handle = document.getElementById('handle');
    let timer = null;
    handle.addEventListener('input', () => {
      updateHandlePreview();
      clearTimeout(timer);
      timer = setTimeout(checkHandleAvailability, 350);
    });
    updateHandlePreview();
  }

  function updateHandlePreview() {
    const preview = document.getElementById('handlePreview');
    preview.textContent = value('handle') || 'handle';
  }

  async function checkHandleAvailability() {
    const status = document.getElementById('handleStatus');
    const handle = value('handle');
    if (!handle) { status.textContent = ''; status.className = 'muted'; return; }
    if (!/^[a-z0-9](?:[a-z0-9-]{1,28}[a-z0-9])?$/.test(handle)) {
      status.textContent = '3~30자 소문자/숫자/하이픈만, 앞뒤 하이픈은 쓸 수 없습니다.';
      status.className = 'error';
      return;
    }
    try {
      const res = await fetchAuth('/api/tutor/academy/handle-available?handle=' + encodeURIComponent(handle));
      if (!res.ok) throw new Error();
      const body = await res.json();
      status.textContent = body.available ? '사용 가능한 주소입니다.' : '이미 사용 중이거나 사용할 수 없는 주소입니다.';
      status.className = body.available ? 'success' : 'error';
    } catch (_) {
      status.textContent = '';
      status.className = 'muted';
    }
  }

  function initIntroCounter() {
    updateIntroCount();
    intro.addEventListener('input', updateIntroCount);
  }

  function updateIntroCount() {
    introCount.textContent = `${intro.value.length}/1000`;
  }

  function initRegionSelects() {
    [1, 2, 3].forEach((i) => {
      const city = document.getElementById(`city${i}`);
      city.innerHTML = cityOptions(i === 1);
      city.addEventListener('change', () => populateDistrict(i));
      populateDistrict(i);
    });
  }

  function cityOptions(required) {
    const empty = required ? '<option value="">시/도 선택</option>' : '<option value="">선택 안 함</option>';
    return empty + Object.keys(REGION_OPTIONS)
      .map((city) => `<option value="${city}">${city}</option>`)
      .join('');
  }

  function populateDistrict(index, selectedDistrict = '') {
    const city = document.getElementById(`city${index}`).value;
    const district = document.getElementById(`district${index}`);
    const districts = city ? REGION_OPTIONS[city] || [] : [];
    district.disabled = !city;
    district.innerHTML = '<option value="">전 지역</option>' + districts
      .filter((name) => name !== '전 지역')
      .map((name) => `<option value="${name}">${name}</option>`)
      .join('');
    // 저장된 구/군이 현재 시/도 목록에 없어도(과거 데이터/표기 차이) 값을 보존해 수정 저장이 막히지 않게 한다.
    ensureOption(district, selectedDistrict);
    district.value = selectedDistrict || '';
  }

  async function loadSubjects() {
    const res = await fetch('/api/subjects');
    const subjects = res.ok ? await res.json() : [];
    const select = document.getElementById('subjectIds');
    select.innerHTML = subjects.map((s) =>
      `<option value="${s.subjectId}">${escapeHtml(s.nameKo || s.nameEn || '과목')}</option>`
    ).join('');
  }

  async function loadExistingAcademy() {
    try {
      const res = await fetchAuth('/api/tutor/academy/me');
      if (res.status === 404) return;
      if (!res.ok) throw new Error('Academy 정보를 불러오지 못했습니다.');
      existingAcademy = await res.json();
      fillAcademy(existingAcademy);
    } catch (err) {
      if (!String(err.message || '').includes('Unauthorized')) {
        status('Academy 정보를 불러오지 못했습니다.', true);
      }
    }
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    status('', false);

    const request = buildRequest();
    if (request.targetGroups.length === 0) return status('대상을 1개 이상 선택해 주세요.', true);
    if (request.regions.length === 0) return status('지역을 1개 이상 입력해 주세요.', true);

    const formData = new FormData();
    formData.append('academy', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    const logo = document.getElementById('logo').files[0];
    const cover = document.getElementById('cover').files[0];
    if (logo) formData.append('logo', logo);
    if (cover) formData.append('cover', cover);

    try {
      const res = await fetchAuth('/api/tutor/academy', {
        method: existingAcademy ? 'PUT' : 'POST',
        body: formData
      });
      if (!res.ok) throw new Error(await res.text());
      const academy = await res.json();
      status('Academy가 저장되었습니다. 수업 상품 생성 화면으로 이동합니다.', false);
      setTimeout(() => location.href = '/tutor/academy', 500);
    } catch (err) {
      status('Academy 저장에 실패했습니다. 필수값과 이미지를 확인해 주세요.', true);
    }
  });

  function buildRequest() {
    const existingSettings = existingAcademy && existingAcademy.pageSettings || {};
    return {
      name: value('name'),
      handle: value('handle'),
      intro: value('intro'),
      subjectIds: [Number(document.getElementById('subjectIds').value)],
      targetGroups: checkedValues('targetGroups'),
      regions: [1, 2, 3].map((i) => ({
        city: value(`city${i}`),
        district: value(`district${i}`) || null
      })).filter((r) => r.city),
      pageSettings: Object.assign({}, existingSettings, {
        blogUrl: value('blogUrl') || null,
        storeUrl: value('storeUrl') || null,
        sectionVisibility: normalizeSectionVisibility(Object.assign(
          {},
          existingSettings.sectionVisibility || {},
          sectionVisibility()
        ))
      })
    };
  }

  function normalizeSectionVisibility(visibility) {
    const normalized = Object.assign({}, visibility || {});
    FUTURE_SECTION_IDS.forEach((id) => {
      normalized[id] = false;
    });
    return normalized;
  }

  function sectionVisibility() {
    const result = {};
    document.querySelectorAll('#sectionVisibility input').forEach((input) => {
      result[input.value] = input.checked;
    });
    return result;
  }

  function fillAcademy(academy) {
    document.getElementById('name').value = academy.name || '';
    // 최초 자동 생성된 임시 handle은 사용자가 직접 공개 주소로 바꿀 수 있게 편집 가능하게 둔다.
    const handleInput = document.getElementById('handle');
    handleInput.value = academy.handle || '';
    handleInput.readOnly = false;
    handleInput.required = true;
    updateHandlePreview();
    document.getElementById('intro').value = academy.intro || '';
    updateIntroCount();
    if (academy.subjects && academy.subjects[0]) {
      document.getElementById('subjectIds').value = academy.subjects[0].subjectId;
    }
    (academy.targetGroups || []).forEach((target) => {
      const input = document.querySelector(`#targetGroups input[value="${target}"]`);
      if (input) input.checked = true;
    });
    (academy.regions || []).slice(0, 3).forEach((region, idx) => {
      const n = idx + 1;
      const citySelect = document.getElementById(`city${n}`);
      // 저장된 시/도가 옵션 목록에 없으면 select.value 세팅이 무시돼 지역이 비워지고
      // 저장 시 "지역을 1개 이상" 으로 막힌다. 값을 옵션으로 보존해 수정이 가능하게 한다.
      ensureOption(citySelect, region.city);
      citySelect.value = region.city || '';
      populateDistrict(n, region.district === 'ALL' ? '' : (region.district || ''));
    });
    const pageSettings = academy.pageSettings || {};
    document.getElementById('blogUrl').value = pageSettings.blogUrl || '';
    document.getElementById('storeUrl').value = pageSettings.storeUrl || '';
    const sectionVisibility = pageSettings.sectionVisibility || {};
    document.querySelectorAll('#sectionVisibility input').forEach((input) => {
      if (sectionVisibility[input.value] === false) input.checked = false;
    });
  }

  function checkedValues(containerId) {
    return Array.from(document.querySelectorAll(`#${containerId} input:checked`)).map((el) => el.value);
  }

  function value(id) {
    return document.getElementById(id).value.trim();
  }

  function status(message, isError) {
    statusMsg.textContent = message;
    statusMsg.className = isError ? 'error' : 'success';
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
  }

  // select에 해당 value 옵션이 없으면 추가한다. 저장된 값(과거 데이터/표기 차이)을
  // select.value로 복원할 수 있게 해, 수정 화면에서 기존 값이 사라지는 것을 막는다.
  function ensureOption(select, val) {
    if (!val) return;
    if (!Array.from(select.options).some((opt) => opt.value === val)) {
      select.insertAdjacentHTML('beforeend', `<option value="${escapeHtml(val)}">${escapeHtml(val)}</option>`);
    }
  }
})();
