(function () {
  const modelContext = document.modelContext;
  if (!modelContext || typeof modelContext.registerTool !== 'function') return;

  async function request(url, options) {
    const response = await window.fetchAuth(url, Object.assign({
      headers: { 'Content-Type': 'application/json' }
    }, options || {}));
    const text = await response.text();
    let body;
    try { body = text ? JSON.parse(text) : {}; } catch (_) { body = { message: text }; }
    if (!response.ok) throw new Error((body && (body.message || body.detail)) || `HTTP ${response.status}`);
    return body;
  }

  const objectSchema = (properties, required) => ({
    type: 'object', properties, required: required || [], additionalProperties: false
  });
  const stringSchema = (description) => ({ type: 'string', description });
  const integerSchema = (description) => ({ type: 'integer', description });
  const evidenceSchema = objectSchema({
    contentType: stringSchema('공개 활동의 사람이 읽을 수 있는 유형'),
    summary: stringSchema('URL에서 직접 확인한 교육적 활동 요약'),
    title: stringSchema('공개 페이지 또는 콘텐츠 제목'),
    sourceUrl: Object.assign(stringSchema('실제 공개 http(s) 근거 URL'), { format: 'uri' }),
    activityDate: Object.assign(stringSchema('근거 활동일 YYYY-MM-DD'), { format: 'date' }),
    evidenceKind: Object.assign(stringSchema('서버 Score 분류'), { enum: [
      'PHILOSOPHY', 'CURRICULUM', 'TEACHING_METHOD', 'MATERIAL', 'STUDENT_GUIDANCE',
      'SUBJECT_EXPERTISE', 'EDUCATIONAL_CONTENT', 'OFFLINE_ACTIVITY',
      'ADVERTISEMENT', 'PRESS_RELEASE', 'RECRUITMENT'
    ] }),
    educationallyInformative: { type: 'boolean', description: '철학·커리큘럼·수업 등 실질 정보 포함 여부' }
  }, ['contentType', 'summary', 'sourceUrl', 'activityDate', 'evidenceKind', 'educationallyInformative']);
  const stringArray = (description, maxItems) => ({ type: 'array', description, items: { type: 'string' }, maxItems: maxItems || 10 });
  const draftSchema = objectSchema({
    regionId: stringSchema('get_next_regions가 반환한 조사 지역 ID'),
    name: stringSchema('개인이 명확한 원장 또는 강사 이름'),
    role: Object.assign(stringSchema('교육자 역할'), { enum: ['원장', '대표', '강사', '선생님'] }),
    academyName: stringSchema('현재 오프라인 학원 또는 교습소'),
    province: stringSchema('공개적으로 확인한 시·도'),
    cityDistrict: stringSchema('공개적으로 확인한 시·군·구'),
    identitySummary: stringSchema('공개 근거에 기반한 교육 정체성 한 문장'),
    educationPhilosophySummary: stringSchema('공개 근거만으로 작성한 교육철학 2~3문장. 일반 광고 문구("학생 중심", "성적 향상")는 불가. 근거가 부족하면 이 후보를 저장하지 마세요.'),
    curriculumSummary: stringSchema('실제 어떤 과정·방법으로 가르치는지 공개 근거로 작성한 2~3문장(예: 진단→개념→적용→오답→반복). 과목 나열(중등영어/수능/내신)만으로는 불가.'),
    distinctiveTeachingSummary: stringSchema('다른 교육자와 구별되는 고유한 수업 방식/구조'),
    recentActivitySummary: stringSchema('현재 연도 활동을 판단한 공개 근거를 사람이 읽기 좋게 요약'),
    strengths: stringArray('BuildIt이 발견한 구체적 강점 2~4개(각 한 문장)', 4),
    improvementSuggestions: stringArray('비난이 아닌 성장 제안 2~3개(각 한 문장)', 3),
    profileImageUrl: Object.assign(stringSchema('공개적으로 사용 가능한 프로필 이미지 URL(있을 때만)'), { format: 'uri' }),
    profileImageSourceUrl: Object.assign(stringSchema('프로필 이미지가 게시된 공개 페이지 URL'), { format: 'uri' }),
    officialHomepage: Object.assign(stringSchema('공식 홈페이지 URL'), { format: 'uri' }),
    officialBlog: Object.assign(stringSchema('공식 블로그 URL'), { format: 'uri' }),
    officialYoutube: Object.assign(stringSchema('공식 YouTube URL'), { format: 'uri' }),
    officialSocial: Object.assign(stringSchema('공식 공개 SNS URL'), { format: 'uri' }),
    discoveryReason: stringSchema('유명세가 아닌 교육적 고유성을 중심으로 한 발견 사유'),
    offlineActivityConfirmed: { type: 'boolean' },
    currentYearActivityConfirmed: { type: 'boolean' },
    educatorIdentityConfirmed: { type: 'boolean' },
    exclusionFree: { type: 'boolean' },
    potentialSignals: { type: 'array', items: { type: 'string' }, maxItems: 20 },
    evidence: { type: 'array', items: evidenceSchema, minItems: 1, maxItems: 30 }
  }, [
    'regionId', 'name', 'role', 'province', 'cityDistrict', 'identitySummary',
    'educationPhilosophySummary', 'curriculumSummary', 'discoveryReason',
    'offlineActivityConfirmed', 'currentYearActivityConfirmed', 'educatorIdentityConfirmed',
    'exclusionFree', 'evidence'
  ]);
  const readOnly = { readOnlyHint: true };
  const write = { readOnlyHint: false, destructiveHint: false, idempotentHint: false };

  async function register(definition) {
    await modelContext.registerTool(definition);
  }

  Promise.all([
    register({
      name: 'get_chart_context',
      description: 'BuildIt Chart의 철학, Hard Gate, 제외 기준, 결정론적 Score 기준과 개인정보 원칙을 읽습니다. 교육자 조사를 시작하기 전에 먼저 호출하세요.',
      inputSchema: objectSchema({}, []), annotations: readOnly,
      execute: async () => request('/api/admin/chart/tools/context')
    }),
    register({
      name: 'get_next_regions',
      description: '아직 조사하지 않았거나 주간 재조사가 필요한 다음 지역을 읽습니다. 지역 상태를 변경하지 않습니다.',
      inputSchema: objectSchema({
        limit: Object.assign(integerSchema('반환할 지역 수. 1~10.'), { minimum: 1, maximum: 10 })
      }, []), annotations: readOnly,
      execute: async ({ limit = 1 } = {}) => request(`/api/admin/chart/tools/regions/next?limit=${encodeURIComponent(limit)}`)
    }),
    register({
      name: 'claim_region',
      description: '다음 조사할 지역을 IN_PROGRESS로 점유합니다. get_next_regions가 반환한 지역만 사용하세요.',
      inputSchema: objectSchema({ regionId: stringSchema('조사 지역 ID') }, ['regionId']), annotations: write,
      execute: async ({ regionId }) => request(`/api/admin/chart/tools/regions/${encodeURIComponent(regionId)}/claim`, { method: 'POST' })
    }),
    register({
      name: 'complete_region',
      description: '이 지역 조사를 완료합니다. selectedCandidateIds에 넣은 후보만 이 지역 최종 선정자로 반영됩니다(save_research_drafts로 저장만 한 후보는 선정이 아님). 적합 교육자가 없으면 selectedCandidateIds를 비우고 호출하면 0명으로 조사 완료됩니다. 서버가 선정자를 전국 누적 publication에 반영한 뒤 지역을 RESEARCHED로 만듭니다.',
      inputSchema: objectSchema({
        regionId: stringSchema('조사 지역 ID'),
        selectedCandidateIds: { type: 'array', items: integerSchema('이 지역에서 최종 선정한 candidate ID'), maxItems: 100, description: '최종 선정자 candidate ID 목록. 0명이면 빈 배열.' },
        selectedCount: integerSchema('선정 인원(교차 확인용, 서버가 실제 값 계산)'),
        rejectedCount: integerSchema('기준 미달로 제외한 인원(참고용)'),
        note: stringSchema('조사 메모')
      }, ['regionId']), annotations: write,
      execute: async ({ regionId, selectedCandidateIds = [], selectedCount, rejectedCount = 0, note = '' }) =>
        request(`/api/admin/chart/tools/regions/${encodeURIComponent(regionId)}/complete`, {
          method: 'POST', body: JSON.stringify({
            selectedCandidateIds,
            selectedCount: (selectedCount === undefined ? selectedCandidateIds.length : selectedCount),
            rejectedCount, note
          })
        })
    }),
    register({
      name: 'search_existing_educators',
      description: '후보 저장 전에 기존 Chart 교육자와 후보의 이름·학원 중복을 조회합니다.',
      inputSchema: objectSchema({
        name: stringSchema('교육자 이름'), academyName: stringSchema('학원 또는 교습소 이름'),
        province: stringSchema('시·도'), cityDistrict: stringSchema('시·군·구')
      }, ['name']), annotations: readOnly,
      execute: async (input) => request('/api/admin/chart/tools/educators/search-existing', {
        method: 'POST', body: JSON.stringify(input)
      })
    }),
    register({
      name: 'save_research_drafts',
      description: '공개 웹에서 조사한 교육자 후보와 evidence URL을 비공개 초안으로 저장합니다. 서버 Hard Gate를 통과해야 하며 공개 Chart는 변경하지 않습니다.',
      inputSchema: objectSchema({
        drafts: {
          type: 'array', minItems: 1, maxItems: 20,
          description: '서버 DraftRequest 계약을 따르는 조사 초안 목록',
          items: draftSchema
        }
      }, ['drafts']), annotations: write,
      execute: async ({ drafts }) => {
        const saved = [];
        for (const draft of drafts) {
          saved.push(await request('/api/admin/chart/tools/research-drafts', {
            method: 'POST', body: JSON.stringify(draft)
          }));
        }
        return { saved, count: saved.length, published: false };
      }
    }),
    register({
      name: 'preview_buildit_scores',
      description: 'Hard Gate 통과 후보의 BuildIt Score를 서버 기준으로 계산합니다. AI가 점수를 결정하지 않으며 저장·공개하지 않습니다.',
      inputSchema: objectSchema({
        candidateIds: { type: 'array', minItems: 1, maxItems: 100, items: integerSchema('후보 ID') }
      }, ['candidateIds']), annotations: readOnly,
      execute: async ({ candidateIds }) => ({
        previews: await Promise.all(candidateIds.map(id =>
          request(`/api/admin/chart/tools/scores/preview?candidateId=${encodeURIComponent(id)}`))),
        published: false
      })
    }),
    register({
      name: 'submit_chart_review_batch',
      description: '누가·어떤 근거로·어떤 Score로 선정했는지를 audit 기록으로 남깁니다(관리자 사전 승인 gate 아님). 실제 공개는 complete_region 시 서버가 자동 처리합니다.',
      inputSchema: objectSchema({
        regionId: stringSchema('조사 지역 ID'),
        candidateIds: { type: 'array', minItems: 1, maxItems: 100, items: integerSchema('후보 ID') },
        summary: stringSchema('조사 audit 요약')
      }, ['regionId', 'candidateIds', 'summary']), annotations: write,
      execute: async (input) => request('/api/admin/chart/tools/review-batches', {
        method: 'POST', body: JSON.stringify(input)
      })
    })
  ]).then(() => {
    document.documentElement.dataset.chartSiteTools = 'ready';
  }).catch(error => {
    console.warn('BuildIt Chart Site tools registration failed', error);
  });
})();
