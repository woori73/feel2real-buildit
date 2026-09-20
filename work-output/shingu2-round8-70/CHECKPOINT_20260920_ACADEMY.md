# 8회 70문항 — 학원 PC CHECKPOINT (2026-09-20)

- Source of Truth: `origin/docs/shingu2-round8-70-20260919` @ `37a85523` → worktree `feel2real-buildit-shingu2r8`,
  브랜치 `docs/shingu2-round8-70-academy-20260920`(트래킹: origin/docs/shingu2-round8-70-20260919).
- 완료: ① 신구중3 4회 등록본(canonical)의 EduHolic 대형 로고 헤더를 문제지 첫 페이지에 이식(본문 70 qblock 무변경,
  headerH 121.25px 재실측 → 패킹 불변, 10페이지 유지) ② `신구중2_8회_70문항_출제검수.md` 신규 작성(BLUEPRINT·HTML·QA·Wiki 자료원 실대조)
  ③ 인쇄 재QA: 문제지·정답해설 각 10p, `qa_pdf.py` 전 20p PASS, 헤더/1·10쪽 래스터 육안 확인.
- 정답 분포 ①14 ②14 ③14 ④14 ⑤14, runs>=4 없음, 문제지↔정답해설 70/70 일치 유지.
- 미완/후속: 내신 Wiki 신규 8회 등록(register_weekly_exam_draft, REVIEW 상태)은 본 커밋 push 완료 후에만 수행.
  최종 승인/공개 전환(APPROVED/FINAL/PUBLISHED)은 운영자 육안 확인 후 별도 — 본 파이프라인에서 수행 금지.
- 환경 한계: 학원 PC 내장 브라우저 조작 타임아웃(unusable) → 시각 QA는 헤드리스 Chrome PDF 래스터로 대체.
