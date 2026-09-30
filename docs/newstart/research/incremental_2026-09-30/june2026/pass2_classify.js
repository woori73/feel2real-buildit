// 2차 relevance 분류 (title+cmt 기반 DRAFT). 입력: accepted_draft.txt
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, 'scan');
const acc = fs.readFileSync(path.join(dir, 'accepted_draft.txt'), 'utf8').split('\n').filter(l => /^26\.06\./.test(l));
const cmt = l => { const m = l.match(/댓글 (\d+)/); return m ? +m[1] : 0; };
const P1 = /신청|접수|심사|보완|실효|전환|확정|임시안|1회차|첫납입|납입|합의서|결과|당첨|통과|부동의|동의|등기|내용증명|예비|청문|실사|재산신고|서류|지원결정|차주전환|방문|유예|연체4|90일|30일 이후|완료/;
const P3 = /기준|제도|감면|이자|채무|담보|재산|근저당|압류|경매|신용점수|신용등급|워크아웃|개인회생|파산|대위변제|연체|보증|대출|캠코|장기연체|추심|계좌|통장|소득|매출|사업자|가압류|질문|문의|방법|어떻|어디|언제/;
const NOISE = /^(안녕|반가|고마|감사|사랑|파이팅|화이팅|힘내|오늘 하루|인사)/;
const buckets = { P1: [], P3: [], LOW: [] };
for (const l of acc) {
  if (P1.test(l)) buckets.P1.push(l);
  else if (NOISE.test((l.split('|')[1] || '').trim())) buckets.LOW.push(l);
  else if (P3.test(l)) buckets.P3.push(l);
  else buckets.LOW.push(l);
}
const byCmt = (a, b) => cmt(b) - cmt(a);
buckets.P1.sort(byCmt); buckets.P3.sort(byCmt); buckets.LOW.sort(byCmt);
const out = [
  '# ACCEPTED 490 2차 relevance 분류 DRAFT — 2026-09-30 ACADEMY (title+cmt heuristic, 원문 미확인)',
  '# P1: 신청/심사/보완/실효/전환/결과 진행사례 후보 — 원문+댓글 수집 1순위',
  '# P3: 제도/재산/채무/담보 등 정보성 후보 — 2순위 (P2 상담사·직원 답변 여부는 원문에서만 판정 가능)',
  '# LOW: P4(일반 질문)/NOISE 구분 대기자 — 원문 확인 전 수집·INSERT 금지',
  '',
  '[P1] ' + buckets.P1.length, ...buckets.P1, '',
  '[P3] ' + buckets.P3.length, ...buckets.P3, '',
  '[LOW_PENDING] ' + buckets.LOW.length, ...buckets.LOW, ''
].join('\n');
fs.writeFileSync(path.join(dir, 'june_relevance_pass2_draft.txt'), out);
console.log('accepted', acc.length, 'P1', buckets.P1.length, 'P3', buckets.P3.length, 'LOW', buckets.LOW.length);
