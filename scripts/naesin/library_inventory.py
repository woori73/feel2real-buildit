"""Classify Library snapshots without mutating Library or any service database."""
import argparse
import collections
import hashlib
import html
import json
import re
import urllib.parse
import zipfile
from pathlib import Path

MAX_SOURCE_BYTES = 384 * 1024 * 1024
ACADEMIC = re.compile(r"내신|수능|오답|학습지|모의고사|학력평가|신구중|압구정중|언북중|신사중|고[123]|중[123]|문법|어휘|독해|단어|영어|문제지|정답|해설|HWP_편집시험")
MID = re.compile(r"중(?:학교)?[ _-]*([123])(?:학년)?|중등")
HIGH = re.compile(r"고(?:등학교)?[ _-]*([123])(?:학년)?|고등|수능|모의고사|학력평가|오답\s*\d+\s*회")
FORMATS = {"hwp", "hwpx", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "html", "htm", "txt", "md", "json", "jsonl", "csv", "tsv", "jpg", "jpeg", "png", "webp", "mp3", "wav", "flac", "mp4", "mov"}

def unwrap(value):
    while isinstance(value, dict) and "items" not in value and "structuredContent" in value:
        value = value["structuredContent"]
    return value

def inventory(directory):
    unique = {}
    for p in sorted(Path(directory).glob("inventory-*.json")):
        for item in unwrap(json.loads(p.read_text()))["items"]:
            key = (item.get("library_file_id") or item.get("file_id") or item["id"], item["path"])
            unique[key] = item
    return list(unique.values())

def label(text):
    m = MID.search(text)
    h = HIGH.search(text)
    if m and h:
        if bool(m.group(1)) == bool(h.group(1)): return "UNCLASSIFIED", "미확인"
        if h.group(1): return "HIGH", "고" + h.group(1)
    if m:
        grade = "중" + m.group(1) if m.group(1) else "중등공통"
        return "MIDDLE", grade
    m = HIGH.search(text)
    if m:
        return "HIGH", "고" + m.group(1) if m.group(1) else "고등공통"
    return "UNCLASSIFIED", "미확인"

def role(text):
    if re.search(r"편집시험|수정시험", text): return "TEST_COPY_NOT_FOR_PUBLICATION"
    if re.search(r"인계|handoff", text, re.I): return "HANDOFF"
    if re.search(r"검수|검토|보고서|로그|worklog|프롬프트", text, re.I): return "PROCESS"
    if re.search(r"정답|해설|해답|답지", text): return "ANSWER_EXPLANATION"
    if re.search(r"문제|학습지|기출|독해|문법|어휘|단어", text): return "QUESTION_OR_SOURCE"
    return "REFERENCE"

def classify(item, originals=None, local_index=None):
    path = urllib.parse.unquote(item["path"])
    name = urllib.parse.unquote(item["name"])
    text = path + "/" + name
    if not ACADEMIC.search(text): return None
    if re.search(r"/AI_AGENT/|AI_AGENT_EP|사업계획|지원사업|스타트업|모두의창업", text, re.I): return None
    division, grade = label(text)
    evidence = "파일명·폴더"
    school = ""
    m = re.search(r"(?:^|[/_\s-])([가-힣]{2,6}중)(?:학교)?(?=[123_/\s.\-]|$)", path)
    if m: school = m.group(1)
    if school and division == "UNCLASSIFIED": division, grade = "MIDDLE", "중등공통"
    if division == "MIDDLE" and grade == "중등공통":
        m = re.search(r"(?:중학교|중[_/\s-]+)[^\n]{0,20}?([123])학년", text)
        if m: grade = "중" + m.group(1)
    ext = Path(name).suffix.lower().lstrip(".")
    local = Path(originals) / item["path"].lstrip("/") if originals else None
    if local and local_index:
        local = local_index.get(item.get("library_file_id"), local)
    sha = None; error = None; verified = False
    if local and local.is_file():
        size = local.stat().st_size
        if size != item["size_bytes"]: error = "SIZE_MISMATCH"
        else:
            digest = hashlib.sha256()
            with local.open("rb") as f:
                for block in iter(lambda: f.read(1024 * 1024), b""): digest.update(block)
            sha = digest.hexdigest(); verified = True
        if division == "UNCLASSIFIED" and ext in {"html", "htm", "txt", "md", "json", "jsonl"}:
            with local.open("rb") as f: body = f.read(200000).decode("utf-8", errors="replace")
            body = html.unescape(re.sub(r"<[^>]*>", " ", body))
            d, g = label(body)
            if d != "UNCLASSIFIED": division, grade, evidence = d, g, "본문 앞부분 학교급 표기(검수 필요)"
        if division == "UNCLASSIFIED" and ext == "zip":
            try:
                with zipfile.ZipFile(local) as z:
                    names = [i.filename for i in z.infolist()[:2000] if not i.is_dir()]
                levels = {label(n)[0] for n in names} - {"UNCLASSIFIED"}
                if len(levels) == 1:
                    division = levels.pop(); grade = "중등공통" if division == "MIDDLE" else "고등공통"; evidence = "ZIP 내부 파일명(검수 필요)"
            except (zipfile.BadZipFile, OSError): pass
    year = re.search(r"20[12][0-9]", text)
    semester = re.search(r"[12][ _-]*학기", text)
    exam = "중간" if "중간" in text else "기말" if "기말" in text else "모의고사" if re.search(r"모의고사|학력평가",text) else "수능" if "수능" in text else "범위 미확인"
    exam_range = " ".join(x for x in [year.group() if year else "", semester.group() if semester else "", exam] if x)
    family = re.sub(r"\(\d+\)$", "", Path(name).stem)
    version = re.search(r"(?:^|[_\s-])([vr]\d+(?:[._]\d+)*)", family, re.I)
    family = re.sub(r"(?:[_\s-])(?:[vr]\d+(?:[._]\d+)*|최종[^_]*|수정[^_]*|FINAL|문제지|정답해설|정답|해설)(?=[_\s.-]|$)", "", family, flags=re.I)
    family = re.sub(r"[_\s]+", "_", family).strip("_")
    status = "CLASSIFICATION_REVIEW" if division == "UNCLASSIFIED" else "HASH_PENDING" if not verified else "READY_FOR_ISOLATED_STAGE"
    classification_status = "UNRESOLVED" if division == "UNCLASSIFIED" else "CONFIRMED_METADATA" if evidence == "파일명·폴더" else "PROPOSED_REVIEW_REQUIRED"
    if classification_status == "PROPOSED_REVIEW_REQUIRED": status = "CLASSIFICATION_REVIEW"
    if ext not in FORMATS: status = "UNSUPPORTED_FORMAT_REVIEW"
    if item["size_bytes"] > MAX_SOURCE_BYTES: status = "OVERSIZE_REVIEW"
    if ext in {"json", "jsonl", "txt", "md", "csv", "tsv"} and item["size_bytes"] > 20*1024*1024: status = "OVERSIZE_TEXT_REVIEW"
    if error: status = "INTEGRITY_FAILURE"
    return {"library_file_id": item.get("library_file_id"), "file_id": item.get("file_id"), "version_id": item.get("version_id"), "name": item["name"], "path": item["path"],
            "size_bytes": item["size_bytes"], "created_at": item.get("created_at"), "modified_at": item.get("modified_at"), "division": division, "grade": grade, "school": school,
            "exam_range": exam_range, "role": role(text), "family": family, "version_label": version.group(1) if version else None, "relationship_status": "PROPOSED_REVIEW_REQUIRED",
            "classification_evidence": evidence, "classification_status": classification_status, "rights_status": "PENDING_REVIEW", "approved": False, "sha256": sha, "bytes_verified": verified, "status": status, "error": error,
            "local_relative_path":local.relative_to(originals).as_posix() if local and originals and local.is_file() else item["path"].lstrip("/")}

def build(items, originals=None):
    local_index = {}
    if originals:
        import os
        for p in Path(originals).rglob("*"):
            if p.is_file():
                try: local_index[os.getxattr(p,"user.library-file-id").decode()] = p
                except OSError: pass
    records = [r for x in items if x["kind"] == "file" and (r := classify(x, originals, local_index))]
    seen = {}; global_seen = {}; families = collections.defaultdict(list)
    for r in records:
        key = (r["division"], r["school"], r["grade"], r["exam_range"], r["sha256"])
        r["duplicate_of"] = None
        if r["sha256"]:
            if key in seen: r["duplicate_of"] = seen[key]
            else: seen[key] = r["library_file_id"]
            global_seen.setdefault(r["sha256"], []).append(r["library_file_id"])
        families[(r["division"],r["grade"],r["school"],r["family"])].append(r["library_file_id"])
    for r in records:
        r["related_library_file_ids"] = [i for i in families[(r["division"],r["grade"],r["school"],r["family"])] if i != r["library_file_id"]]
    summary = {}
    for level in ("MIDDLE", "HIGH", "UNCLASSIFIED"):
        rows = [r for r in records if r["division"] == level]
        summary[level] = {"found":len(rows), "bytes_verified":sum(r["bytes_verified"] for r in rows), "duplicate_excluded":sum(bool(r["duplicate_of"]) for r in rows),
            "production_registered":0,"registration_failed":0,"integrity_failed":sum(r["status"]=="INTEGRITY_FAILURE" for r in rows),
            "grade_counts":dict(collections.Counter(r["grade"] for r in rows)),"school_counts":dict(collections.Counter(r["school"] or "공통·학교 미확인" for r in rows)),
            "status_counts":dict(collections.Counter(r["status"] for r in rows))}
    identity = hashlib.sha256(json.dumps([(r["library_file_id"],r["file_id"],r["division"],r["grade"],r["sha256"]) for r in records],ensure_ascii=False).encode()).hexdigest()
    return {"format":"naesin-library-import-v1","manifest_sha256":identity,"inventory_files":sum(x["kind"]=="file" for x in items),"candidate_count":len(records),
        "production_execution_authorized":False,"production_registered":0,"approval_status":"AWAITING_DEPLOYMENT_APPROVAL", "rights_status":"PENDING_REVIEW",
        "classification_method":"파일명·폴더와 필요한 경우 로컬 본문 앞부분/ZIP 목록. 관계는 검수 전 제안이며 무명 이미지의 내용 전수 판독은 아님.","summary":summary,"records":records}

def main():
    p=argparse.ArgumentParser();p.add_argument("--inventory-dir",required=True);p.add_argument("--originals");p.add_argument("--output",required=True)
    a=p.parse_args(); result=build(inventory(a.inventory_dir),a.originals);Path(a.output).write_text(json.dumps(result,ensure_ascii=False,indent=2))
    print(json.dumps({"inventory_files":result["inventory_files"],"candidate_count":result["candidate_count"],"summary":result["summary"]},ensure_ascii=False))
if __name__=="__main__": main()
