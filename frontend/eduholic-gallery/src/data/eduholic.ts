/**
 * Real eduholic.feel2real.co.kr content (captured 2026-09-16 via browser).
 * No fictional SaaS copy — every string below exists on the live academy page.
 */
const ACAD = '%EC%95%95%EA%B5%AC%EC%A0%95%EC%97%90%EB%93%80%ED%99%80%EB%A6%AD';
export const LIVE = 'https://eduholic.feel2real.co.kr';
// Media paths are same-origin relative: under Spring they resolve on the
// live host directly; under vite dev they resolve via server.proxy.
// These are the ORIGINAL academy API media URLs (no placeholders).
export const ACAD_API = `/api/academies/${ACAD}`;

export const eduholic = {
  academy: '압구정에듀홀릭학원',
  director: '박정민 원장',
  directorTitle: '압구정에듀홀릭학원장',
  heroQuote:
    '영어 수업과 함께 아이의 일상과 고민도 자연스럽게 나눕니다. 박정민 원장이 한 명 한 명의 특징을 파악하여, 부담 없이 꾸준히 이어갈 수 있는 환경을 만듭니다.',
  heroVideo: `/video/eduholic/principal-intro.mp4`,
  principalPhoto: `${ACAD_API}/hero-media/principal`,
  logo: `${ACAD_API}/logo`,
  introVideo: `${ACAD_API}/videos/13/stream`,
  introVideoPoster: `${ACAD_API}/videos/13/thumbnail`,
  liveFeatureVideo: `${ACAD_API}/videos/21/stream`,
  liveFeatureVideoPoster: `${ACAD_API}/videos/21/thumbnail`,
  popupVisual: `/api/academies/eduholic/midterm-campaign/popup-visual`,
  popup: {
    kicker: 'MIDTERM LIVE',
    title: '2026 2학기 중간고사 내신 LIVE',
    desc: '박정민 원장님의 시스템화된 내신 준비 (학교별 Digital Twin, 내신 자료 제작 현황) 지금 확인하세요.',
    primary: '준비 현장 보기 →',
    dismiss: '오늘은 그만 보기',
  },
  stats: [
    { value: 58, prefix: '35–', suffix: '만원', label: '월 수강료', animate: true },
    { value: 87, prefix: '', suffix: '%', label: '성적 향상률', animate: true },
    { value: 20, prefix: '', suffix: '년', label: '운영 경력', animate: true },
  ],
  credentials: [
    '덕성여대 어린이영어지도사 과정 & TESOL 강사',
    '중앙공무원교육원 국제협력실 사무관',
    '부산외국어대학교 전임강사',
    '미 국방외국어대학 교수',
    '랭카스터 대학교 응용언어학과 박사 수료',
    '미 샌프란시스코 주립대학교 TESOL 석사',
  ],
  livePage: `${LIVE}/eduholic-midterm-2026.html`,
  liveFeature: {
    title: '신구중3 실제 제작 과정 · 53초',
    desc: '학교 프린트와 수업 Insight를 읽고, 출제 패턴을 분석해 예상문제·정답·해설을 만듭니다.',
    tag: '학교 자료 × Digital Twin × AI',
  },
  schools: [
    {
      name: '압구정중',
      grade: '2학년',
      scope: 'Lesson 5',
      point: '핵심 문법 · 간접의문문 · 5형식',
      wiki: `${LIVE}/eduholic-midterm-2026.html`,
      analysis: '/analyzes/11',
      thumb: `${ACAD_API}/lessons/9/hero-image`,
    },
    {
      name: '신구중',
      grade: '3학년',
      scope: 'Lesson 6',
      point: '학교 프린트 × 출제 패턴 분석',
      wiki: `${LIVE}/eduholic-midterm-2026.html`,
      analysis: '/analyzes/12',
      thumb: `${ACAD_API}/lessons/10/hero-image`,
    },
    {
      name: '신사중',
      grade: '3학년',
      scope: '핵심 문법 · used to',
      point: '시험범위 Wiki + 출제 분석',
      wiki: `${LIVE}/eduholic-midterm-2026.html`,
      analysis: '/analyzes/13',
      thumb: `${ACAD_API}/lessons/11/hero-image`,
    },
    {
      name: '언북중',
      grade: '3학년 · 1학기 · 기말',
      scope: '출제 경향',
      point: '출제자 분석 2건',
      wiki: `${LIVE}/eduholic-midterm-2026.html`,
      analysis: '/analyzes/10',
      thumb: `${ACAD_API}/videos/8/thumbnail`,
    },
  ],
  classVideos: [
    { id: 8, duration: '0:17', thumb: `${ACAD_API}/videos/8/thumbnail`, title: '실제 수업 현장 ①' },
    { id: 6, duration: '0:55', thumb: `${ACAD_API}/videos/6/thumbnail`, title: '실제 수업 현장 ②' },
    { id: 4, duration: '0:56', thumb: `${ACAD_API}/videos/4/thumbnail`, title: '실제 수업 현장 ③' },
    { id: 3, duration: '0:54', thumb: `${ACAD_API}/videos/3/thumbnail`, title: '실제 수업 현장 ④' },
  ],
  curriculum: [
    { name: 'F2R Starter', sub: '영문법 기초: 레고', desc: '영어의 기본 블록을 분류하고 조립하는 단계', target: '초등 고학년 – 중1', img: `${ACAD_API}/lessons/9/hero-image` },
    { name: 'F2R Booster', sub: '영문법 중급: 버블티', desc: '문장 구성 단위를 취향에 맞춰 커스텀하는 단계', target: '중1 – 중2', img: `${ACAD_API}/lessons/10/hero-image` },
    { name: 'F2R Master', sub: '영문법 고급: 가우디', desc: "'5품사 구조'로 문장의 설계도를 그리는 단계", target: '중3 – 고1', img: `${ACAD_API}/lessons/11/hero-image` },
    { name: '중학교 영어내신', sub: '압구정중 · 신구중 · 신사중 · 언북중', desc: '출제자의 눈으로 풀고, 함정을 미리 파악해 감점을 제로화합니다.', target: '중1 – 중3', img: `${ACAD_API}/videos/6/thumbnail` },
  ],
  updates: [
    { kicker: '내신 LIVE', text: '2학기 영어 내신 LIVE — 학교별 실제 자료를 읽고 예상문제를 만듭니다.' },
    { kicker: '시험범위', text: '압구정중 2학년 Lesson 5 · 간접의문문 · 5형식 Wiki 공개' },
    { kicker: '출제 분석', text: '신구중 3학년 Lesson 6 · 학교 프린트 × 출제 패턴 분석' },
    { kicker: '출제 분석', text: '언북중 3학년 1학기 기말 · 출제자 분석 2건' },
    { kicker: '커리큘럼', text: 'F2R Starter · Booster · Master 대표 수업 안내' },
  ],
  cta: {
    counsel: '상담 신청하기',
    viewClass: '대표 수업 보기',
    phone: '02-6324-1453',
    address: '서울시 강남구 압구정스퀘어 206호',
    blog: 'https://blog.naver.com/apgujungeduholic',
  },
};
