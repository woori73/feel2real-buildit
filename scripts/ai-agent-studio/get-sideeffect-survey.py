#!/usr/bin/env python3
"""MCP 읽기 정책(read-policy.json)의 GET 경로가 외부 비용·쓰기 부수효과를 가질 수 있는지 정적으로 조사한다.

컨트롤러 메서드 본문과 그 메서드가 호출하는 주입된 서비스 메서드 본문(2단계)에서 키워드를 찾는다.
휴리스틱이므로 '문제 없음'의 증명이 아니라 '사람이 확인해야 할 후보' 목록이다.
사용: python3 -I scripts/ai-agent-studio/get-sideeffect-survey.py > docs/ai-agent-studio/GET_SIDE_EFFECT_SURVEY.md
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java"
POLICY = ROOT / "src/main/resources/mcp/read-policy.json"

CATEGORIES = {
    "외부 AI 비용": re.compile(r"anthropic|openai|claude|musespark|llmtransport|\bllm\b|embedding|\.embed\(|chatclient", re.I),
    "외부 HTTP": re.compile(r"HttpClient|RestClient|RestTemplate|WebClient|HttpRequest\.newBuilder|URL\(", re.I),
    "DB·파일 쓰기": re.compile(r"\.save\(|\.saveAll\(|\.saveAndFlush\(|\.delete\w*\(|\.insert\w*\(|executeUpdate|jdbc\w*\.update\(|Files\.(write|delete|move|copy)|storage\.save|\.persist\(|\.merge\("),
    "프로세스·셸": re.compile(r"ProcessBuilder|Runtime\.getRuntime"),
}

MAPPING = re.compile(r"@(Get|Request|Post|Put|Delete|Patch)Mapping\s*(?:\(([^)]*)\))?", re.S)
PATHS = re.compile(r'"([^"]*)"')


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def block_after(src: str, start: int) -> str:
    i = src.find("{", start)
    if i < 0:
        return ""
    depth = 0
    for j in range(i, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                return src[i : j + 1]
    return src[i:]


classes = {}  # simple name -> (path, stripped source)
for f in JAVA.rglob("*.java"):
    classes[f.stem] = (f, strip_comments(f.read_text(encoding="utf-8", errors="replace")))


def methods_of(src: str):
    """(name, body) 목록. 단순 시그니처 정규식 기반."""
    out = []
    for m in re.finditer(r"(?:public|protected|private|static|\s)+[\w<>\[\],.? ]+\s+(\w+)\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\s*\{", src):
        out.append((m.group(1), block_after(src, m.start())))
    return out


def endpoints():
    """(joined_path, controller, method, body) for GET-capable mappings."""
    found = []
    for name, (path, src) in classes.items():
        if "Controller" not in name and "@RestController" not in src and "@Controller" not in src:
            continue
        class_prefixes = [""]
        cm = re.search(r"@RequestMapping\s*\(([^)]*)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?class\s", src, re.S)
        if cm:
            class_prefixes = PATHS.findall(cm.group(1)) or [""]
        for mm in MAPPING.finditer(src):
            kind = mm.group(1)
            args = mm.group(2) or ""
            if kind == "Request" and "GET" not in args and "method" in args:
                continue
            if kind in ("Post", "Put", "Delete", "Patch"):
                continue
            if kind == "Request" and cm and mm.start() == cm.start():
                continue
            # 메서드 본문: 매핑 뒤 첫 시그니처
            body = block_after(src, mm.end())
            sig = re.search(r"(\w+)\s*\([^;{]*\)\s*(?:throws [\w., ]+)?\s*\{", src[mm.end() : mm.end() + 1500])
            mname = sig.group(1) if sig else "?"
            for sub in PATHS.findall(args) or [""]:
                for pre in class_prefixes:
                    found.append(((pre + sub).replace("//", "/") or "/", name, mname, body))
    return found


def service_calls(body: str, src: str):
    """본문에서 field.method( 형태 호출을 찾아 해당 필드 타입의 메서드 본문을 반환."""
    fields = dict(re.findall(r"(?:private|final)\s+(?:final\s+)?([A-Z]\w+)\s+(\w+)\s*;", src))
    fields = {v: k for k, v in fields.items()}
    bodies = []
    for fld, meth in set(re.findall(r"\b(\w+)\.(\w+)\(", body)):
        typ = fields.get(fld)
        if typ and typ in classes:
            for n, b in methods_of(classes[typ][1]):
                if n == meth:
                    bodies.append((f"{typ}.{meth}", b))
    return bodies


MANUAL_REVIEW = """
## 수동 검토 결과 (후보 15건)

| 경로 | 판정 | 근거 |
|---|---|---|
| `/api/newstart/consultations/{id}`, `/api/admin/newstart/consultations/{id}` | **실제 쓰기 있음(읽음 표시 save)** | `NewStartConsultationService.thread/adminThread`가 GET에서 `consultationRepository.save`. MCP 경유 시 읽기 전용 트랜잭션+강제 rollback으로 DB 반영이 차단된다(= 외부 AI 조회는 '읽음' 처리하지 않음). 정책 유지, 통합 테스트로 회귀 확인 권장 |
| `/api/admin/newstart/jipijigi/inventory` | 오탐 | 'embedding' 문자열은 안내 문구. count 조회만 수행 |
| `/api/wemade/**` 6건 | 오탐 | `sourceUrl` 필드 문자열. 외부 HTTP 호출 없음(저장 분석 자료) |
| `/api/admin/easy/summary`, `/api/chart/regions` | 오탐(키워드 우연 일치) | 집계 조회 |
| `/api/studio/files/{id}/text` | 무해 | ZIP 목록 미리보기용 임시 파일 생성 후 삭제(압축 해제 없음) |
| `/sitemap.xml` | 오탐 | URL 문자열 조립 |
| `/api/academies/{handle}/midterm-campaign/live-videos` | 확인 필요 | `MidtermLiveVideoService.listPublic`의 외부 HTTP 후보. 저장 목록 조회인지 운영 전 확인 |

외부 AI 비용(LLM·임베딩 호출)을 일으키는 GET 정책은 발견되지 않았다. 한계: 정적 해석 불가 108건(프록시·뷰)은 이 조사에 포함되지 않았으며, 프록시 대상(AcademyCraft·Wiki Python)은 업스트림 코드를 별도로 확인해야 한다.
"""


def main():
    policy = json.loads(POLICY.read_text(encoding="utf-8"))
    eps = endpoints()
    index = {}
    for p, c, m, b in eps:
        index.setdefault(p, []).append((c, m, b))
    rows, unresolved, clean = [], [], 0
    for item in policy:
        path = item["path"]
        hits = index.get(path)
        if not hits:
            unresolved.append((path, item["service"], "프록시/뷰 컨트롤러/정적 매핑 — 업스트림 또는 템플릿"))
            continue
        flags = {}
        for c, m, body in hits:
            texts = [(f"{c}.{m}", body)] + service_calls(body, classes[c][1])
            for label, text in texts:
                for cat, rx in CATEGORIES.items():
                    if rx.search(text):
                        flags.setdefault(cat, set()).add(label)
        if flags:
            rows.append((path, item["service"], flags))
        else:
            clean += 1
    print("# MCP GET 부수효과·외부 비용 정적 조사 (자동 생성)\n")
    print("`scripts/ai-agent-studio/get-sideeffect-survey.py`가 생성했다. **후보 목록이며 안전성의 증명이 아니다.**\n")
    print(f"- 정책 경로: {len(policy)}건 · 키워드 미검출: {clean}건 · 후보: {len(rows)}건 · 정적으로 해석 불가(프록시/뷰/템플릿): {len(unresolved)}건\n")
    print("검사 범위: 컨트롤러 GET 메서드 본문 + 호출하는 주입 서비스 메서드 본문(2단계). "
          "리포지토리 `@Modifying`·AOP·이벤트 리스너·3단계 이상 호출은 보지 못한다. MCP는 추가로 읽기 전용 트랜잭션+rollback(Java)과 SQLite `mode=ro`(Wiki)로 DB 쓰기를 차단한다. "
          "외부 HTTP·AI·파일 쓰기는 이 트랜잭션이 막지 못하므로 아래 후보를 사람이 검토해야 한다.\n")
    print("## 후보\n")
    print("| 경로 | 서비스 | 분류 | 근거 메서드 |\n|---|---|---|---|")
    for path, svc, flags in sorted(rows):
        cats = ", ".join(sorted(flags))
        ev = "; ".join(sorted({x for v in flags.values() for x in v}))[:160]
        print(f"| `{path}` | {svc} | {cats} | {ev} |")
    print("\n## 정적으로 해석 불가 (업스트림/뷰)\n")
    print("| 경로 | 서비스 | 사유 |\n|---|---|---|")
    for path, svc, why in sorted(unresolved):
        print(f"| `{path}` | {svc} | {why} |")
    print(MANUAL_REVIEW)


if __name__ == "__main__":
    sys.exit(main())
