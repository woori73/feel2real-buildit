#!/usr/bin/env python3
"""Build the private Question Wiki reference corpus from Eduholic PDFs.

The PDF is the source of truth.  Text spans, coordinates and underline drawings
are retained so a record can be reconstructed without returning to OCR.
"""
from __future__ import annotations

import argparse
import gzip
import html
import json
import re
from collections import Counter
from pathlib import Path

import fitz

CIRCLED = "①②③④⑤"
HEAD_RE = re.compile(r"^(\d{1,3})\.\s*(.*)$")
SOURCE_RE = re.compile(r"\[(?:from|출처)[^\]]*\]", re.I | re.S)


def reading_lines(page):
    drawings = [d["rect"] for d in page.get_drawings()
                if d["rect"].height <= 1.5 and d["rect"].width >= 3]
    spans = []
    for block in page.get_text("dict")["blocks"]:
        for line in block.get("lines", []):
            for span in line.get("spans", []):
                text = span["text"]
                if not text.strip():
                    continue
                x0, y0, x1, y1 = span["bbox"]
                underlined = any(
                    abs(r.y0 - y1) <= 3.2 and r.x1 > x0 + .5 and r.x0 < x1 - .5
                    for r in drawings
                )
                spans.append({
                    "text": text, "bbox": [round(v, 2) for v in span["bbox"]],
                    "font": span["font"], "size": round(span["size"], 2),
                    "bold": "Bold" in span["font"], "underline": underlined,
                })
    # The source is a two-column A4 print. Read the entire left column first.
    for s in spans:
        s["column"] = 0 if s["bbox"][0] < page.rect.width / 2 else 1
    spans.sort(key=lambda s: (s["column"], s["bbox"][1], s["bbox"][0]))
    lines = []
    for s in spans:
        if (not lines or lines[-1]["column"] != s["column"]
                or abs(lines[-1]["y"] - s["bbox"][1]) > 2.1):
            lines.append({"column": s["column"], "y": s["bbox"][1], "spans": [s]})
        else:
            lines[-1]["spans"].append(s)
    for line in lines:
        line["spans"].sort(key=lambda s: s["bbox"][0])
        line["text"] = "".join(s["text"] for s in line["spans"]).strip()
        baseline = max(s["bbox"][3] for s in line["spans"])
        line_rects = [r for r in drawings if abs(r.y0 - baseline) <= 3.2]
        line["underlineRects"] = [[round(v, 2) for v in (r.x0,r.y0,r.x1,r.y1)] for r in line_rects]
        words=[]
        for x0,y0,x1,y1,text,*_ in page.get_text("words"):
            if abs(y0-line["y"]) > 2.5: continue
            if any(r.x1 > x0+.3 and r.x0 < x1-.3 for r in line_rects):
                words.append({"text":text,"bbox":[round(v,2) for v in (x0,y0,x1,y1)]})
        line["underlinedWords"] = words
    return lines


def clean_lines(lines):
    out = []
    for line in lines:
        t = line["text"]
        y = line["y"]
        if y < 50 or y > 805 or t == "에듀홀릭" or re.fullmatch(r"-\s*\d+\s*-", t):
            continue
        out.append(line)
    return out


def split_records(pdf_path):
    doc = fitz.open(pdf_path)
    records, current, expected = [], None, 1
    answer_pages = []
    for page_no, page in enumerate(doc, 1):
        page_text = page.get_text()
        if "(정답지)" in page_text:
            answer_pages.append(page_no)
            continue
        for line in clean_lines(reading_lines(page)):
            line["pdfPage"] = page_no
            m = HEAD_RE.match(line["text"])
            if m:
                no = int(m.group(1))
                # Real variant numbers are strictly sequential; other leading
                # numbers in prose/conditions are retained in the current item.
                if no == expected:
                    if current:
                        records.append(current)
                    current = {"variantIndex": no, "pages": [], "layoutLines": []}
                    expected += 1
                    line = dict(line)
                    line["text"] = m.group(2).strip()
                    if line["spans"]:
                        first = dict(line["spans"][0])
                        first["text"] = re.sub(r"^\d{1,3}\.\s*", "", first["text"])
                        line["spans"] = [first] + line["spans"][1:]
            if current:
                if page_no not in current["pages"]:
                    current["pages"].append(page_no)
                current["layoutLines"].append(line)
    if current:
        records.append(current)
    return doc, records, answer_pages


def classify(instruction):
    compact = re.sub(r"\s+", "", instruction)
    if "문맥에맞는낱말" in compact or "낱말의쓰임" in compact or "어휘" in compact:
        return "vocabulary"
    if "어법" in compact:
        return "grammar"
    if "글의순서" in compact or "이어질글" in compact:
        return "order"
    if "주어진문장" in compact and "들어가기에" in compact:
        return "insertion"
    if "요약" in compact:
        return "summary"
    if "주관식" in compact or "서술" in compact or "조건" in compact:
        return "written"
    if "빈칸" in compact:
        return "blank"
    if "제목" in compact or "주제" in compact or "요지" in compact:
        return "title_topic"
    return "other"


def parse_record(record, grade, source_file, answers):
    lines = record.pop("layoutLines")
    text_lines = [x["text"] for x in lines if x["text"]]
    raw = "\n".join(text_lines).strip()
    bracket = re.search(r"\[(20|21|22|23|24|29|30|31|32|33|34|35|36|37|38|39|40|41~42)\]", raw)
    source_label = bracket.group(1) if bracket else None
    source_nos = [41, 42] if source_label == "41~42" else ([int(source_label)] if source_label else [])
    source_no = "41-42" if source_label == "41~42" else (source_nos[0] if source_nos else None)
    # Instruction ends at the source-number bracket and can wrap over lines.
    instruction_parts = []
    for line in text_lines:
        instruction_parts.append(line)
        if re.search(r"\[(?:20|21|22|23|24|29|30|31|32|33|34|35|36|37|38|39|40|41~42)\]", line):
            break
    instruction = " ".join(instruction_parts)
    ptype = classify(instruction)
    choices = []
    for mark, body in re.findall(r"([①②③④⑤])\s*([^①②③④⑤]+?)(?=(?:[①②③④⑤])|$)", raw, re.S):
        body = re.sub(r"\s+", " ", body).strip()
        if body:
            choices.append({"no": CIRCLED.index(mark) + 1, "text": body})
    underlined = []
    last_mark = None
    for line in lines:
        for span in line["spans"]:
            marks = [c for c in span["text"] if c in CIRCLED]
            if marks:
                last_mark = marks[-1]
        if line.get("underlinedWords"):
            text=" ".join(w["text"] for w in line["underlinedWords"])
            if last_mark and re.search(r"[A-Za-z]", text) and not re.search(r"[가-힣]", text):
                underlined.append({"mark":last_mark,"text":text,
                    "page":line["pdfPage"],
                    "wordBoxes":line["underlinedWords"],"underlineRects":line["underlineRects"]})
    blocks = {}
    for label in "ABC":
        m = re.search(rf"\({label}\)\s*(.*?)(?=\([ABC]\)|[①②③④⑤]|$)", raw, re.S)
        if m:
            blocks[label] = re.sub(r"\s+", " ", m.group(1)).strip()
    given = None
    if ptype == "insertion":
        rest = raw[bracket.end():].strip() if bracket else raw
        m = re.match(r"(.+?[.!?])(?:\s|\n)+(?=[A-Z“‘])", rest, re.S)
        given = re.sub(r"\s+", " ", m.group(1)).strip() if m else None
    result = {
        "grade": grade, "examYear": 2026, "examLabel": "9월 전국연합",
        "sourceQuestionNo": source_no, "sourceQuestionNos": source_nos,
        "variantIndex": record["variantIndex"],
        "type": ptype, "instruction": instruction, "rawText": raw,
        "rawLayout": lines,
        "provenance": {"sourceFile": source_file, "pdfPages": record["pages"],
                       "sourceQuestionNo": source_no, "sourceQuestionNos": source_nos,
                       "variantIndex": record["variantIndex"]},
        "choices": choices, "answer": answers.get(record["variantIndex"]),
    }
    if ptype in ("grammar", "vocabulary"):
        result["testedSegments"] = underlined
        if len({x["mark"] for x in underlined}) >= 3:
            result["choices"] = []  # ①-⑤ are in-passage targets, not choices.
    elif ptype == "blank":
        blank_rects=[]
        for line in lines:
            if line.get("underlineRects") and not line.get("underlinedWords"):
                blank_rects.extend({"page":line["pdfPage"],"bbox":box} for box in line["underlineRects"])
        result["blank"] = {"present": "___" in raw or "빈칸" in instruction,
                           "originalText": None, "rects":blank_rects}
    elif ptype == "order":
        result["blocks"] = blocks
    elif ptype == "insertion":
        result["givenSentence"] = given
        points=[]
        for line_no,line in enumerate(lines):
            for s in line["spans"]:
                for c in s["text"]:
                    if c in CIRCLED:
                        points.append({"no":CIRCLED.index(c)+1,"page":line["pdfPage"],
                                       "line":line_no,"bbox":s["bbox"]})
        result["insertionPoints"] = points
        result["choices"] = []  # circled numbers are positions inside the passage.
    elif ptype in ("written", "summary"):
        result["conditions"] = [x for x in text_lines if "조건" in x]
        result["blanks"] = re.findall(r"\([A-Z]\)\s*_+|ⓐ|ⓑ", raw)
    return result


def parse_answers(doc, answer_pages):
    text = "\n".join(doc[p-1].get_text("text", sort=True) for p in answer_pages)
    matches = list(re.finditer(r"(?m)^\s*(\d{1,3})\s*번\s*-\s*", text))
    out = {}
    for i, m in enumerate(matches):
        value = text[m.end():matches[i+1].start() if i+1 < len(matches) else len(text)].strip()
        symbol = re.match(r"([①②③④⑤]|\d(?:\s*,\s*\d)*)", value)
        out[int(m.group(1))] = {"raw": value, "choice": CIRCLED.index(symbol.group(1))+1
                                      if symbol and symbol.group(1) in CIRCLED else None}
    return out


def validate(r):
    errors, reviews = [], []
    for key in ("grade", "sourceQuestionNo", "type", "instruction", "rawText", "provenance"):
        if not r.get(key): errors.append(f"missing:{key}")
    if r["type"] in ("grammar", "vocabulary"):
        marks = [x.get("mark") for x in r.get("testedSegments", []) if x.get("mark")]
        if "밑줄" in r["instruction"] and len(set(marks)) < 3:
            reviews.append("tested underline/mark association incomplete")
    if r["type"] == "blank" and not r.get("blank", {}).get("present"):
        errors.append("blank missing")
    if r["type"] == "order" and set(r.get("blocks", {})) != set("ABC"):
        reviews.append("A/B/C extraction incomplete")
    if r["type"] == "insertion":
        if not r.get("givenSentence"): reviews.append("given sentence extraction incomplete")
        if len({x["no"] for x in r.get("insertionPoints", [])}) < 5: reviews.append("insertion points incomplete")
    if errors: return "FAIL", errors + reviews
    if reviews: return "REVIEW", reviews
    return "PASS", []


def render_debug(records, target):
    cards=[]
    for r in records:
        body=[]
        for line in r["rawLayout"]:
            spans=[]
            for s in line["spans"]:
                style="text-decoration:underline" if s["underline"] else ""
                spans.append(f'<span style="{style}">{html.escape(s["text"])}</span>')
            body.append("".join(spans))
        cards.append(f'<article><h2>{r["variantIndex"]}. [{r["sourceQuestionNo"]}] {r["type"]} - {r["validation"]["status"]}</h2><div class="box">'+"<br>".join(body)+"</div></article>")
    target.write_text('<!doctype html><meta charset="utf-8"><style>body{font:14px Arial;margin:24px}article{break-inside:avoid;margin:0 0 32px}.box{border:1px solid #bbb;padding:16px;line-height:1.55}h2{font-size:16px}</style>'+"".join(cards), encoding="utf-8")


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--grade", type=int, required=True, choices=(1,2))
    ap.add_argument("--pdf", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--debug-html", type=Path)
    ap.add_argument("--pilot", type=int, default=0)
    ap.add_argument("--shard-dir", type=Path)
    args=ap.parse_args()
    doc, raw_records, answer_pages = split_records(args.pdf)
    answers=parse_answers(doc, answer_pages)
    records=[parse_record(r,args.grade,args.pdf.name,answers) for r in raw_records]
    for r in records:
        status, reasons=validate(r); r["validation"]={"status":status,"reasons":reasons}
    if args.pilot:
        # Take diverse records, then fill to requested size in source order.
        chosen=[]
        for typ in ("grammar","vocabulary","blank","order","insertion","title_topic","written","summary"):
            chosen += [r for r in records if r["type"]==typ][:3]
        seen=set(); records=[r for r in chosen+records if not (r["variantIndex"] in seen or seen.add(r["variantIndex"]))][:args.pilot]
    payload={"schemaVersion":2,"grade":args.grade,"sourceFile":args.pdf.name,
             "recordCount":len(records),"typeCounts":dict(Counter(r["type"] for r in records)),
             "validationCounts":dict(Counter(r["validation"]["status"] for r in records)),
             "records":records}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8")
    if args.shard_dir:
        args.shard_dir.mkdir(parents=True,exist_ok=True)
        grouped={}
        for record in records:
            grouped.setdefault(str(record["sourceQuestionNo"]),[]).append(record)
        shards=[]
        for source_no, items in grouped.items():
            name=f"g{args.grade}-q{source_no}.json.gz"
            raw=json.dumps({"schemaVersion":2,"grade":args.grade,"sourceQuestionNo":source_no,
                            "recordCount":len(items),"records":items},
                           ensure_ascii=False,separators=(",",":")).encode()
            with gzip.open(args.shard_dir/name,"wb",compresslevel=9) as stream: stream.write(raw)
            shards.append({"sourceQuestionNo":source_no,"file":name,"recordCount":len(items)})
        manifest={k:payload[k] for k in ("schemaVersion","grade","sourceFile","recordCount","typeCounts","validationCounts")}
        manifest["shards"]=shards
        (args.shard_dir/f"grade{args.grade}-manifest.json").write_text(
            json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    if args.debug_html: render_debug(records,args.debug_html)
    print(json.dumps({k:payload[k] for k in ("recordCount","typeCounts","validationCounts")},ensure_ascii=False))


if __name__ == "__main__": main()
