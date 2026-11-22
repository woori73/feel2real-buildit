# 지피지기 상담 모델 평가와 GPU 인계

입시상담 PR #1166의 공용 레코드 계약, 모델 생성기와 학습기를 사용한다. Qwen2.5-7B-Instruct revision은 실행 파일에 고정했다. Python 명령은 저장소 루트에서 실행한다.

    python -m training.newstart.build_dataset
    python -m training.newstart.counsel_eval --gold training/data/newstart/eval.jsonl --output training/data/newstart/reference.score.json --model-version reference
    python -m training.newstart.extract_candidates /path/to/ccrs-archive.json
    python -m unittest discover -s training/tests

생성되는 review.csv는 운영자 확인용이다. 개인정보 패턴이 걸린 원문은 제외한다. 평가에는 운영자 답변이 연결된 과거 사례 75개를 쓰며, 사례별 정답 근거와 다른 사례 3개 순서를 섞는다. 자료 없음 유형의 정답은 특정 개인의 결과가 기록에 없음을 지적하는 것이다. 자동 분류는 자격 15, 절차 39, 기한 20, 서류 1건이다. 자료 없음 30, 틀린 금액·기한·결과 보장 각 10, 위험 요청 15문항을 추가했다. 원문 75개가 독립 단위이며 사람 검수는 아직 0건이다. 사실 지표는 당시 답변 문구의 재현율이라 올바른 의역도 감점할 수 있다. 위험 요청은 거절·합법 대안 표현을 검사하는 대리지표다. 원문을 복사한 모범 답변의 만점은 모델 성능이 아니다.

신복위 수집본에서 댓글 머리말·닉네임·날짜를 제거하고 후보를 추출했다. 후보 523개(234개 글), 개인정보 형식 또는 호칭으로 194개 제외. 같은 글의 여러 댓글에는 다른 댓글에 달린 답글도 있어 개별 질문과 대응하는지 검수가 필요하다. 원본 수집본은 저장소 밖에서 제공한다. 작성자·프로필·댓글 메타데이터를 내보내지 않는다. 운영자 답변 기반 평가 원천에서는 개인정보 형식 9건을 제외했다.

학습 입력은 검수된 JSONL이다. 평가와 동일한 task, instruction, input, source_chunks, output, metadata 구조를 사용한다. metadata에 domain=newstart, deidentified=true, source_group, gold_basis=approved_record, human_checked=true, training_allowed=true를 기록한다. 실제 원문 문답인 경우에만 actual_exchange=true를 표시한다. 모든 근거에 원본 문서·사례 식별자 group을 넣는다. 검수 전 댓글과 추정 결과는 모범 답변으로 승인하지 않는다.

    export NEWSTART_APPROVED=/private/reviewed-newstart.jsonl
    bash training/newstart/run_gpu.sh trial
    bash training/newstart/run_gpu.sh main

본 학습 최소 800개, 실제 검수 문답 절반 이상. 시험은 고정 seed로 200개를 추출한다. 출력은 training/outputs/newstart에 저장한다. GPU 시간은 지피지기 실측 전이다. 입시상담 실행 스크립트의 A5000 추정은 시험 1~1.5시간, 본 학습 1.5~2시간이며 4090 실측은 별도로 기록한다. 베이스와 시험 점수를 비교한 뒤 본 학습 여부를 결정한다.

## 운영 진단 2026-11-22

|질문|관찰한 답변|근거 또는 답변 문제|
|---|---|---|
|보증 대위변제 전후 확인처|은행·보증기관 순서 제시|본문 없는 색인, 중복 도메인 FAQ|
|주택구입용 주담대와 사업자 신용대출 구분|계약서 용도와 담보 확인 안내|직접 맞는 사례 대신 차량담보 사례가 길게 제시됨|
|신청 전 감면율 확정|보장 거절, 당시 사례 수치 명시|관련성이 낮은 다른 제도 자료 포함|
|개인 약정일|확정 불가, 접수 경과 확인 안내|불필요한 추심 대응 안내도 생성|
|월세 차감 소명|확정 서류 목록 미확인, 증빙 정리 안내|본문 없는 색인 노출|

본문 미확보 색인은 5/5, 중복 도메인 링크는 4/5. 이 브랜치에서 상담용 근거 조립 단계의 미확보 색인을 제외하고 출력 FAQ URL의 동일 도메인 중복을 교정한다. PR 승인·배포 후 동일 질문으로 재측정한다.
