# DesignCraft #716 운영 적용 승인 요청서

상태: 검증 완료, 승인 요청 제출. **현재 운영 설치·전환·재시작·배포·DB 변경 승인은 없다.** 아래 명령은 계획이며 실행하지 않았다.

## 확인한 운영 조건

- Amazon Linux 2023.11.20260514, x86_64, glibc 2.34.
- 서비스 `f2r-app.service`, 사용자 `ec2-user`.
- unit `/etc/systemd/system/f2r-app.service`, 기존 drop-in `/etc/systemd/system/f2r-app.service.d/override.conf`, 환경파일 `/etc/f2r/env`. 환경 값은 수집/노출하지 않았다.
- CLI 안정 경로 `/opt/designcraft/designcraft-cli`, 템플릿 `/opt/designcraft/templates`, 현재 저장소 `/tmp/publishing`(이전 실행 설정 조사).
- 총 메모리 약 1.92 GiB, 조사 시 가용 약 320 MiB, swap 2 GiB. `/opt` 파일시스템 가용 약 9.63 GiB. 순간 측정이며 적용 직전에 다시 읽기 전용 확인 필요.
- 기존 CLI SHA-256 `64a467a06dceac46a7cca5990e46946faa1027d3b33228f1c42caaff9e33da87`, GLIBC_2.35 요구로 실행 불가.

## 승인 대상 산출물 (고정)

- DesignCraft 0.8.0, source `11a51d5b665aec8d6e675bd107a8a01514f10d00`, Rust 1.95.0, AL2023 glibc 2.34 격리 빌드.
- **binary SHA-256 `2edd60ef2f575263a31ad6cb1ec661181bd24207d4aefbec8516b04990700335`** (44,959,664 bytes).
- **배포 tar.gz SHA-256 `5d4a3e7b9a0569ed904ae41d777c1a02854e579fd8aaa2769a34a4c3228aee6d`** (18,950,047 bytes).
- [최종 artifact 및 검증 SUCCESS](https://github.com/woori73/feel2real-buildit/actions/runs/38850191210/artifacts/12024728328), 보존 기한 2026-11-15 UTC. ZIP 외피와 내부 tar.gz 체크섬은 다르며 이 승인 대상은 내부 tar.gz 및 CLI다.
- runtime: glibc 2.34의 libc/libm/ld-linux, libgcc_s(GCC_4.2.0 이하 심볼). Rust/Python/컴파일러/X11 설치 불필요. 기존 Document Fonts의 NotoSansKR-Regular/Bold를 재사용.
- 세 한글 템플릿 반입·조판·PDF, 기존 0.6 native의 한글/표 재열기 검증 PASS. 비특권 UID 65534 PDF 생성 PASS, 소형 fixture CLI peak RSS 약 48 MiB. 대문서/동시 부하와 운영에서의 canary는 미실행.

## 승인 요청 A — 엔진 파일 설치 및 안정 경로 전환

요청 범위: 검증된 호환 CLI만 새 버전 디렉터리에 설치, 기존 CLI·설정·템플릿/프로젝트 파일 백업, 안정 경로 원자 전환, 서비스 사용자로 synthetic 파일 기반 canary, 실패 시 즉시 CLI 경로 롤백.

**시스템 glibc/OS/패키지 업그레이드, AWS 설정, DB 작업, Spring 재시작, 앱 배포, 사용자 실제 문서 변경은 요청 범위에 없다.** 운영에서 컴파일하지 않는다.

새 경로는 `/opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57/bin/designcraft-cli`, 기존 앱 설정은 `/opt/designcraft/designcraft-cli` 그대로 유지한다. CLI는 작업마다 ProcessBuilder로 새로 실행되므로 안정 경로의 symlink 교체는 다음 프로세스부터 반영된다.

승인 후 절차:

1. 공개 검증 산출물을 다운로드하고 **archive SHA와 내부 binary SHA를 둘 다** 확인한다. 보존 기간이 지난 Actions artifact는 임의 대체하지 않고 동일 입력으로 다시 빌드·검증한 뒤 해시 변경을 재승인받는다.
2. 새 조판 요청이 없는 유지보수 시간에 기존 CLI 프로세스가 없는지 읽기 전용 확인한다. 실행 중 작업이 있으면 기다린다. 요청을 막기 위해 nginx/AWS/DB/전역 설정을 임의 변경하지 않는다. 요청 부재를 확보할 수 없으면 전환하지 않는다.
3. root 전용 백업 디렉터리 `/var/backups/designcraft/<CHANGE_ID>`(0700)에 기존 CLI의 실제 파일과 symlink 여부, 환경파일·unit/drop-in, 템플릿과 현재 프로젝트 파일을 `cp -a` 또는 tar로 보관한다. 비밀값·문서 내용을 로그/외부로 출력하지 않는다. 파일 규모를 먼저 확인해 공간이 부족하면 진행하지 않는다.
4. 새 디렉터리와 실행파일은 root 소유, 디렉터리 0755/CLI 0755. `ec2-user`가 읽고 실행 가능하고 코드/공유라이브러리를 쓸 수 없도록 한다. 배포 패키지의 licenses/Cargo.lock/container digest와 검증 manifest도 버전 디렉터리에 보관한다. 기존 템플릿/한글 폰트는 변경하지 않는다.
5. 전환 전에 `runuser -u ec2-user -- <새CLI> --version`, `ldd <새CLI>`의 누락 없음, 공개 synthetic 복사본의 한글 조판/PDF를 전용 임시 폴더에서 확인한다. 운영 문서/DB는 입력으로 사용하지 않는다. 파일 canary 자체도 이 승인 A 이후에만 수행한다.
6. `/opt/designcraft/.designcraft-cli.<CHANGE_ID>`에 새 CLI를 가리키는 임시 symlink를 만들고 `mv -Tf`로 `/opt/designcraft/designcraft-cli`에 원자 교체한다. 기존 안정 경로를 먼저 삭제하지 않는다. SHA·version만 기록한다.
7. health GET과 파일 canary, 새 CLI 오류/OOM 여부를 본문 없는 카운터로 확인한다. Spring API로 작업을 생성하는 운영 E2E는 DB 쓰기를 동반하므로 이번 승인 A에 포함하지 않는다.

명령 골격(아직 실행하지 않음, 해시/CHANGE_ID 확정 후 적용):

```bash
# root 전용 셸; 실제 키/문서/환경값을 echo/cat 하지 않는다.
install -d -m 0700 /var/backups/designcraft/<CHANGE_ID>
cp -a --dereference /opt/designcraft/designcraft-cli /var/backups/designcraft/<CHANGE_ID>/designcraft-cli.bin
cp -a /etc/f2r/env /var/backups/designcraft/<CHANGE_ID>/f2r.env
chmod 0600 /var/backups/designcraft/<CHANGE_ID>/f2r.env
# unit/drop-in, 템플릿/프로젝트의 파일 백업은 현재 크기·경로 확인 후 같은 보호 디렉터리로 수행.
install -d -m 0755 /opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57/bin
install -o root -g root -m 0755 <검증된CLI> /opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57/bin/designcraft-cli
# SHA/ldd/서비스 사용자 canary 확인 후에만:
ln -s /opt/designcraft/releases/0.8.0-al2023-2edd60ef2f57/bin/designcraft-cli /opt/designcraft/.designcraft-cli.<CHANGE_ID>
mv -Tf /opt/designcraft/.designcraft-cli.<CHANGE_ID> /opt/designcraft/designcraft-cli
```

예상 영향: 엔진 파일 교체는 원자적이며 **BuildIt 서비스 중단/재시작 0회**를 계획한다. 요청을 받지 않는 조판 유지보수 창은 준비·canary 포함 **5~10분 추정**이며 실측 SLA가 아니다. 새 CLI 단일 소형 PDF의 격리 측정 peak RSS는 약 48 MiB다. 바이너리 약 43 MiB·배포 archive 약 18 MiB와 구 CLI 백업·프로젝트 백업 크기만큼 디스크가 추가된다. 약 320 MiB 가용 메모리인 운영에서 대문서/동시 AI 작업은 검증하지 않았으므로 작은 canary부터 적용하고 메모리/OOM 이상이면 전환을 중단한다.

롤백: 새 CLI 프로세스가 끝난 뒤 백업 CLI를 root 소유 임시 파일(0751)에 복사하여 같은 `mv -Tf`로 안정 경로를 복원한다. 설정·JAR·템플릿·프로젝트는 승인 A에서 변경하지 않으므로 통상 복원 대상이 아니다. **구 CLI 자체는 glibc 비호환이므로 롤백은 변경 철회이며 DesignCraft 정상화를 보장하지 않는다.** 시스템 glibc 롤백 작업은 발생하지 않는다.

## 별도 승인 B — #716 병합 및 애플리케이션 배포

승인 A는 #716 main 병합 또는 앱 배포 승인이 아니다. 코드 배포는 모바일 패널·AI 승인 편집 수정의 운영 반영에 필요하지만, 별도 PR 병합/배포 승인 이후에만 진행한다.

- 앱 JAR `/home/ec2-user/app.jar` 및 이전 JAR·환경·unit/drop-in을 먼저 백업.
- DB/Flyway 실행을 포함하는 기존 배포 워크플로를 이 승인 A로 호출하지 않는다. 배포 전 미적용 migration 유무와 배포 범위를 읽기 전용 확인하고 DB 변경은 별도 승인받는다.
- 앱 배포 시 `f2r-app.service` 재시작이 필요하며 BuildIt 전체 요청에 영향을 준다. 예상 중단 **1~3분**, 실패·롤백 여유 포함 **10분 유지보수 창**을 제안한다. 현재 배포 가드의 health 예산은 PID당 24×5초, 최대 3세대이므로 장애 시 6분 이상 걸릴 수 있다. 무중단으로 표현하지 않는다.
- 실패 시 이전 JAR/환경/CLI 경로 복원 후 서비스 재시작 및 health 확인. DB 롤백/운영 문서 일괄 되돌리기는 자동 수행하지 않는다.

## #715 / HWPX

#715는 #716에 포함된 채 유지한다. #716 병합 성공 및 main CI 확인 후 '포함됨/대체됨'으로 종료 제안. 지금 종료하거나 중복 병합하지 않는다.

DesignCraft→HWPX 내보내기는 별도 구현 범위이며 이번 운영 적용 대상이 아니다. 기존 HWPX 셀 수정 기능과 구분한다.
