# DesignCraft #716 Draft 해제 및 운영 교체 사전점검
확인 시각: 2026-10-16 17:09 KST 이후. 현재 승인 범위는 Draft 해제만이며, 아래 운영 명령은 실행하지 않았다.

## PR 처리 완료
- head `f244717581c5dce7413858407ca80fb95d833f03` 유지.
- 최신 main `abc7bebcdb2b167cca0fc9666c75cd6993e27301`, GitHub mergeable=true / clean. 이전 시험 병합 이후 main 변경 없음.
- Build Check #1693(run 38847868856)의 build/mysql-integration-test SUCCESS, DesignCraft QA #28(run 38847868904) SUCCESS 재확인. QA 재실행 없음.
- 사용자 승인에 따라 Draft 해제 완료(draft=false), main 병합 안 함.
- #715는 #716 병합 후 main CI 성공까지 유지한다. 종료/중복 병합하지 않았다.

## 현재 사전점검의 실제 결과
[운영 읽기 전용 실행 38871839790](https://github.com/woori73/feel2real-buildit/actions/runs/38871839790)은 AWS 자격증명 획득 단계에서 실패:
`Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity`.
SSM 명령 전송 단계가 실행되지 않았으므로 서버 파일/프로세스/DB는 건드리지 않았다. 권한 우회·신뢰정책 변경·다른 자격증명 시도·반복 재시도는 하지 않았다.

| 항목 | 확인 결과 |
|---|---|
| 현재 운영 SHA/설정 경로 | 이번 재확인 차단. 아래 값은 이전 성공한 읽기 전용 조사 기록 |
| 현재 CLI 실행 프로세스/진행 중 작업 | 미확인. 0건이라고 판단하지 않음 |
| 대기 작업 수 | DB 조회 안 함. 프로세스 0건이어도 대기 작업 0건을 뜻하지 않음 |
| 신규 작업 차단 수단 | 현재 활성화된 차단 수단 미확인. 코드에 검증된 온라인 유지보수/drain 기능 없음 |
| 교체 파일 | 기존 최종 artifact 유효/미만료 확인. 재빌드하지 않음 |

기존 진단 역할의 OIDC 접근이 복구돼야 현재 서버 상태를 읽기 전용으로 재확인할 수 있다. 이번 승인으로 AWS 정책을 고칠 수는 없다.

## 고정한 파일·경로
- 운영 안정 경로(이전 확인): `/opt/designcraft/designcraft-cli`
- 구 0.6 binary SHA-256: `64a467a06dceac46a7cca5990e46946faa1027d3b33228f1c42caaff9e33da87`
- 교체 CLI SHA-256: `2edd60ef2f575263a31ad6cb1ec661181bd24207d4aefbec8516b04990700335`
- 배포 tar.gz SHA-256: `5d4a3e7b9a0569ed904ae41d777c1a02854e579fd8aaa2769a34a4c3228aee6d`
- 새 설치 경로: `/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57/bin/designcraft-cli`
- [검증 artifact](https://github.com/woori73/feel2real-buildit/actions/runs/38850191210/artifacts/12024728328), 만료 2026-11-15 UTC. 위 tar.gz와 CLI 해시는 이 artifact 내부 파일의 값이며 외피 ZIP SHA와 다르다.
- 구 CLI는 glibc 2.35 요구/운영 glibc 2.34로 실행 불가였다. 새 CLI는 glibc 2.34 격리 검증 완료. 시스템 glibc 업그레이드는 금지한다.

## 신규 작업 통제 — 교체 전 반드시 해결할 조건
현재 시점의 pgrep 결과만으로 신규 API 요청과 다음 CLI 실행 사이의 race를 제거할 수 없다. 운영자끼리의 flock도 Spring의 CLI 실행을 차단하지 않는다. 작업 생성 차단만으로는 미리보기/AI 승인 편집/검수/PDF 등 기존 작업의 CLI 호출을 막을 수 없다.

따라서 현 CLI-only 파일 교체안을 지금 그대로 실행하는 것은 보류한다. 교체 직전에 다음이 확보돼야 한다:
1. DesignCraft의 모든 CLI 실행 진입 경로에 대한 신규 요청/자동 호출 차단 또는 검증된 drain.
2. 이미 실행 중인 요청과 CLI 종료 대기. 프로세스를 강제 종료하거나 운영 DB 상태를 수정하지 않는다.
3. 차단 상태 유지 → canary/전환/후검증 → 차단 해제의 명확한 순서.

가능한 방법은 별도 승인된 DesignCraft API 유지보수 차단(다른 서비스는 유지)과 background 호출 중지/종료 확인, 또는 별도 구현·격리 검증된 CLI 공유락 wrapper/배포 배타락이다. **현재 어느 것도 설치/활성화하지 않았고, 새 wrapper를 검증된 기능으로 취급하지 않는다.** nginx 설정/리로드, 서비스 pause/restart, 앱 설정 변경도 기존 CLI 교체 승인에 묵시적으로 포함하지 않는다.

단순한 '이용자가 없을 것 같은 시간'은 강제 통제를 대체하지 않는다. 통제 수단을 승인·검증하기 전에는 아래 전환 명령을 실행하지 않는다. 이전 승인 요청서의 '원자 교체, 서비스 재시작 0회'만으로 전체 교체 안전성이 확보된다는 해석을 이 문서로 보정한다.

## 승인 후 사용할 정확한 설치·복구 명령
아래는 root 전용 shell에서 수행할 계획이다. 먼저 신규 호출 통제와 기존 실행 종료가 확인돼야 한다. 소스 artifact 전송·설치 자체도 미승인이다. /var/tmp의 archive 경로는 승인 후 전달할 고정 경로다. 사용자 문서와 환경파일 값을 출력하지 않는다.

```bash
set -euo pipefail
umask 077
OLD=64a467a06dceac46a7cca5990e46946faa1027d3b33228f1c42caaff9e33da87
NEW=2edd60ef2f575263a31ad6cb1ec661181bd24207d4aefbec8516b04990700335
ARCHIVE=5d4a3e7b9a0569ed904ae41d777c1a02854e579fd8aaa2769a34a4c3228aee6d
CLI=/opt/designcraft/designcraft-cli
REL=/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57
PKG=/var/tmp/designcraft-0.8.0-al2023-x86_64.tar.gz
CHANGE_ID="designcraft-$(date -u +%Y%m%dT%H%M%SZ)"
BK="/var/backups/designcraft/$CHANGE_ID"

printf '%s  %s\n' "$ARCHIVE" "$PKG" | sha256sum -c -
printf '%s  %s\n' "$OLD" "$CLI" | sha256sum -c -
test ! -e "$REL"
test ! -L "$CLI"
test "$(stat -c '%a:%u:%g' "$CLI")" = "751:0:0"
install -d -m 0700 "$BK"
cp -a --dereference "$CLI" "$BK/designcraft-cli-0.6.bin"
printf '%s  %s\n' "$OLD" "$BK/designcraft-cli-0.6.bin" | sha256sum -c -
# 원래 속성과 링크 상태만 보관; 환경값/문서 출력 금지
stat -c '%a %u %g' "$CLI" > "$BK/original-mode-owner.txt"
readlink "$CLI" > "$BK/original-symlink.txt" || true

install -d -m 0755 "$REL"
tar --no-same-owner -xzf "$PKG" -C "$REL"
chown -R root:root "$REL"
chmod -R go-w "$REL"
chmod 0755 "$REL" "$REL/bin" "$REL/bin/designcraft-cli"
printf '%s  %s\n' "$NEW" "$REL/bin/designcraft-cli" | sha256sum -c -
test "$(runuser -u ec2-user -- "$REL/bin/designcraft-cli" --version)" = "designcraft-cli 0.8.0"
ldd "$REL/bin/designcraft-cli" > "$BK/new-ldd.txt"
! grep -q 'not found' "$BK/new-ldd.txt"
# 아래 file canary를 새 경로에서 통과하고 호출 차단/실행 종료 확인 후에만 전환:
ln -s "$REL/bin/designcraft-cli" "/opt/designcraft/.cli-$CHANGE_ID"
mv -Tf "/opt/designcraft/.cli-$CHANGE_ID" "$CLI"
printf '%s  %s\n' "$NEW" "$CLI" | sha256sum -c -
test "$(runuser -u ec2-user -- "$CLI" --version)" = "designcraft-cli 0.8.0"
```

즉시 복구(동일 root shell의 BK/CLI/OLD/CHANGE_ID 사용, 신규 호출 차단을 유지/재확보하고 실행 종료 확인 후):
```bash
printf '%s  %s\n' "$OLD" "$BK/designcraft-cli-0.6.bin" | sha256sum -c -
install -o root -g root -m 0751 "$BK/designcraft-cli-0.6.bin" "/opt/designcraft/.cli-rollback-$CHANGE_ID"
mv -Tf "/opt/designcraft/.cli-rollback-$CHANGE_ID" "$CLI"
printf '%s  %s\n' "$OLD" "$CLI" | sha256sum -c -
```
이는 이전 조사에서 확인한 root:root/0751 일반 파일을 복원하는 명령이다. 교체 직전 경로가 symlink 또는 다른 소유권/모드로 바뀌었다면 중단하고 복구 계획을 갱신한다. 기존 CLI는 실행 불가였으므로 롤백은 변경 철회이며 정상 조판 복구 보장이 아니다.

## 파일 canary와 실패 기준
승인 후 전용 synthetic 작업 폴더만 쓰며 운영 DB/실제 사용자 문서는 사용하지 않는다. 기존 템플릿을 복사하고 ec2-user 소유(0700)로 만들어 조판한다. 새 CLI와 전환 후 안정 경로에 대해 같은 명령 계약을 확인한다:
```bash
CANARY="/var/tmp/$CHANGE_ID-canary"
install -d -o ec2-user -g ec2-user -m 0700 "$CANARY"
cp -a /opt/designcraft/templates/ko-textbook-a4/. "$CANARY/"
chown -R ec2-user:ec2-user "$CANARY"
runuser -u ec2-user -- python3 -c 'from pathlib import Path; import sys; Path(sys.argv[1]).write_text(chr(10).join(["한글 운영 조판 확인","문단과 표 검증은 격리 결과 참조.",""]),encoding="utf-8")' "$CANARY/canary.txt"
# 안정 경로 전환 전에는 ENGINE="$REL/bin/designcraft-cli", 후에는 ENGINE="$CLI"
ENGINE="$CLI"
(
 cd "$CANARY"
 printf '%s\n' 'file.place={"path":"canary.txt","autoflow":true}' 'edit.selectAll={}' 'style.paragraph.apply={"name":"ko-body"}' |
   timeout 30s runuser -u ec2-user -- "$ENGINE" script - --in template.designcraft --save canary.designcraft > "$BK/canary-import.json"
 timeout 30s runuser -u ec2-user -- "$ENGINE" run --in canary.designcraft --cmd document.inspect > "$BK/canary-inspect.json"
 timeout 30s runuser -u ec2-user -- "$ENGINE" run --in canary.designcraft --cmd 'file.exportPdf={"path":"canary.pdf","standard":"none","tagged":true}' > "$BK/canary-export.json"
)
test -s "$CANARY/canary.pdf"
curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health
```
위 exit=0/파일 생성은 1차 canary일 뿐이다. PDF를 보안 경로로 가져와 재열기·한글 문장·폰트와 페이지 표시를 확인하기 전 최종 성공 처리하지 않는다. 운영에 pdftotext/렌더러를 새 설치하지 않는다. Python3는 CLI 의존성이 아니라 기존 설치된 canary 텍스트 생성 도구이며 없으면 운영 패키지 설치 없이 입력파일을 미리 만들어 전달한다.

실패 기준: checksum/path 불일치, version !=0.8.0, missing dependency, 서비스 사용자 실행 실패, 각 CLI 30초 timeout/nonzero, PDF 재열기/한글 내용·폰트 불일치, health 장애 또는 새 CLI와 시간상 관련된 OOM/반복 실행 실패. 전환 전 실패면 전환하지 않고 기존 안정 경로를 유지한다. 전환 후 실패면 신규 호출을 계속 차단한 상태로 위 복구 명령을 수행한다. 현재 자동 rollback hook은 설치하지 않았으며, 수동 롤백 담당자가 대기해야 한다.

## 기존 서비스 경로의 검증 방법과 한계
1. 실행 중 Spring의 publishing CLI 설정 출처/경로를 읽기 전용 재확인(이번은 OIDC 차단).
2. 전환 후 readlink -f와 SHA로 안정 경로→검증된 새 CLI 매핑 확인.
3. 서비스 사용자 ec2-user·동일 안정 경로·작업별 cwd·동일 script/run/inspect/export 명령으로 file canary 확인. 실제 사용자 파일/DB를 건드리지 않는다.
4. 실제 정상 사용자 활동에서 Spring 자식 CLI의 /proc/exe와 PPID만 수동 관찰해 새 binary 실행 여부를 확인할 수 있다. argv/문서 경로/내용은 기록하지 않는다. 활동이 없으면 '실제 Spring 호출 관측 미검증'으로 남긴다.
5. 파일 canary는 Spring 로그인→DB 작업 생성 E2E와 다르다. 운영 E2E는 DB 쓰기 승인이 없으므로 수행하지 않는다.

## 영향과 결론
계획상 CLI/라이선스 디렉터리·백업·synthetic 파일만 쓰고 app JAR·설정·glibc·DB는 변경하지 않는다. 엔진 경로 전환 자체는 서비스 재시작 0회이나 신규 호출 통제 수단의 영향은 아직 확정하지 못했다. 따라서 기존 5~10분 유지보수 창은 추정치이며 현재 '무중단 교체 준비 완료'로 결론내리지 않는다.

**Draft 해제 완료. 운영 교체는 OIDC 접근 복구 후 최신 상태·신규 호출 통제 확인이 끝날 때까지 승인 판단 보류 권장.** #715는 #716 병합 후 main CI 성공까지 그대로 유지.
