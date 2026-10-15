(async function () {
  'use strict';
  const box = document.getElementById('score-list');
  await TP.loadMe();
  if (!TP.user) { box.innerHTML = '<p class="lead">Please <a href="/home">log in</a> to see your scores.<span class="ko">점수를 확인하려면 <a href="/home">로그인</a>하세요.</span></p>'; return; }
  try {
    const rows = await TP.api('/api/testprep/attempts');
    if (!rows.length) { box.innerHTML = '<p class="lead">No attempts yet — start from <a href="/testprep/practice">iBT Practice</a> or <a href="/testprep/vocab">Vocabulary Quiz</a>.<span class="ko">아직 응시 기록이 없습니다 — <a href="/testprep/practice">iBT 모의고사</a> 또는 <a href="/testprep/vocab">어휘 퀴즈</a>에서 시작하세요.</span></p>'; return; }
    box.innerHTML = '<div class="table-scroll"><table class="tp"><tr><th>When<span class="ko">날짜</span></th><th>Exam<span class="ko">시험</span></th><th>Type<span class="ko">종류</span></th><th>Status<span class="ko">상태</span></th><th>Band (internal)<span class="ko">밴드(내부)</span></th><th>Accuracy<span class="ko">정답률</span></th><th></th></tr>'
      + rows.map(r => `<tr>
        <td>${TP.esc((r.startedAt || '').slice(0, 16))}</td>
        <td>${TP.esc(r.examTitle)}<br><span class="meta" style="font-size:12px;color:var(--muted)">${r.questionCount} questions</span></td>
        <td>${TP.esc(r.kind)}</td>
        <td>${TP.esc(r.status)}${r.overtime ? ' ⏱' : ''}</td>
        <td>${r.overallBand != null ? '<strong>' + r.overallBand + '</strong>' : '—'}</td>
        <td>${r.accuracy != null ? r.accuracy + '%' : '—'}</td>
        <td>${r.status === 'SUBMITTED' ? '<a class="btn btn-outline btn-sm" href="/testprep/review?attempt=' + r.attemptId + '">Review</a>'
          : r.status === 'IN_PROGRESS' ? '<a class="btn btn-primary btn-sm" href="/testprep/run?attempt=' + r.attemptId + '">Resume</a>' : ''}</td>
      </tr>`).join('')
      + '</table></div><p class="disclaimer" style="margin-top:12px">Band = TestPrep internal estimate, not an official ETS score.<span class="ko" style="color:#7a5300">밴드 = 공식 ETS 점수가 아닌 TestPrep 내부 추정치입니다.</span></p>';
  } catch (err) {
    box.innerHTML = '<p class="lead">Failed to load: ' + TP.esc(err.message) + '<span class="ko">불러오기 실패: ' + TP.esc(err.message) + '</span></p>';
  }
})();
