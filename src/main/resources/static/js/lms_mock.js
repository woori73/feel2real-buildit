/*
 * 학원 LMS 1단계 MOCK 데이터 — 데이터 리터럴만 둔다.
 *
 * ⚠ 페이지 스크립트는 이 파일을 직접 읽지 않는다. 반드시 lms_data.js(LmsData)를 거친다.
 *   2단계에서 실제 API로 갈아끼울 때 교체 대상이 lms_data.js 한 파일로 고정되기 때문이다.
 *
 * 목업 TalkFile_LMS_V3.html의 createDefaultVideos() + courses 3건을 그대로 이식했다.
 */
(function () {
  'use strict';

  function createDefaultVideos() {
    return [
      { key: 'trailer', type: 'trailer', title: '2분 맛보기', desc: '강좌를 미리 만나보세요.', length: '2분', url: null, fileName: null, uploadDate: null },
      { key: 'ot', type: 'OT', title: 'OT', desc: '강좌 소개 및 오리엔테이션 영상입니다.', length: '10분', url: null, fileName: null, uploadDate: null },
      { key: 's1', type: 'section', title: 'Section 1', desc: '핵심 개념 강의', length: '30분', url: null, fileName: null, uploadDate: null },
      { key: 's2', type: 'section', title: 'Section 2', desc: '문제풀이 강의', length: '30분', url: null, fileName: null, uploadDate: null },
      { key: 's3', type: 'section', title: 'Section 3', desc: '실전 모의고사 강의', length: '30분', url: null, fileName: null, uploadDate: null }
    ];
  }

  var courses = [
    { id: 1, school: '압구정중', grade: '중2', title: '영어 내신특강', desc: '학교학습지를 기반으로 한 변형문제와 실전 모의고사', instructor: '10년 이상 압구정 지역 내신 전문 강의 경력을 보유한 전임 강사가 직접 지도합니다.', target: '압구정중 2학년 내신 대비가 필요한 학생', price: 149000 },
    { id: 2, school: '신구중', grade: '중1', title: '영어 내신특강', desc: '학교학습지를 기반으로 한 변형문제와 실전 모의고사', instructor: '신구중 내신 출제 경향을 오랫동안 분석해온 전임 강사가 지도합니다.', target: '신구중 1학년 내신 대비가 필요한 학생', price: 139000 },
    { id: 3, school: '신구중', grade: '중2', title: '영어 내신특강', desc: '학교학습지를 기반으로 한 변형문제와 실전 모의고사', instructor: '신구중 내신 출제 경향을 오랫동안 분석해온 전임 강사가 지도합니다.', target: '신구중 2학년 내신 대비가 필요한 학생', price: 149000 }
  ];
  courses.forEach(function (c) { c.videos = createDefaultVideos(); });

  // nextCourseId/createDefaultVideos는 내보내지 않는다 — 강좌 등록이 MOCK 행 추가에서
  // 수업 상품 전용 편집기 이동으로 바뀌면서 외부 사용처가 사라졌다.
  window.LMS_MOCK = {
    courses: courses
  };
})();
