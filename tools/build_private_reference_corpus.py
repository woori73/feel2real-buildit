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
        col_x0, col_x1 = ((0,page.rect.width/2) if line["column"]==0 else (page.rect.width/2,page.rect.width))
        line_rects = [r for r in drawings if abs(r.y0 - baseline) <= 3.2
                      and r.x0 >= col_x0 and r.x1 <= col_x1]
        line["underlineRects"] = [[round(v, 2) for v in (r.x0,r.y0,r.x1,r.y1)] for r in line_rects]
        words=[]; all_words=[]
        for x0,y0,x1,y1,text,*_ in page.get_text("words"):
            if abs(y0-line["y"]) > 2.5: continue
            if not (col_x0 <= (x0+x1)/2 < col_x1): continue
            word={"text":text,"bbox":[round(v,2) for v in (x0,y0,x1,y1)]}
            all_words.append(word)
            if any(r.x1 > x0+.3 and r.x0 < x1-.3 for r in line_rects):
                words.append(word)
        line["underlinedWords"] = words
        line["allWords"] = sorted(all_words,key=lambda w:w["bbox"][0])
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
    if "요약" in compact:
        return "summary"
    if "주관식" in compact or "서술" in compact or "조건" in compact:
        return "written"
    if "문맥에맞는낱말" in compact or "낱말의쓰임" in compact or "어휘" in compact:
        return "vocabulary"
    if "어법" in compact:
        return "grammar"
    if "글의순서" in compact or "이어질글" in compact:
        return "order"
    if "주어진문장" in compact and "들어가기에" in compact:
        return "insertion"
    if "빈칸" in compact:
        return "blank"
    if "제목" in compact or "주제" in compact or "요지" in compact:
        return "title_topic"
    if "내용과일치하지않는" in compact: return "content_mismatch"
    if "내용과일치하는" in compact: return "content_match"
    if "의미하는바" in compact: return "implication"
    if "전체흐름과관계없는" in compact: return "irrelevant_sentence"
    if "목적으로가장" in compact: return "purpose"
    if "영영풀이" in compact: return "definition"
    if "답할수없는질문" in compact or "올바르게이해" in compact or "가장이해" in compact:
        return "comprehension"
    if "필자가주장" in compact: return "claim"
    if "가리키는대상" in compact or "쓰임이나머지" in compact: return "reference"
    if "역할로가장" in compact: return "function"
    return "other"


def parse_choices(raw):
    hits=[(i,CIRCLED.index(c)+1) for i,c in enumerate(raw) if c in CIRCLED]
    starts=None
    for i in range(len(hits)-4):
        if [n for _,n in hits[i:i+5]]==[1,2,3,4,5]: starts=hits[i:i+5]
    if not starts: return []
    choices=[]
    for i,(pos,no) in enumerate(starts):
        end=starts[i+1][0] if i<4 else len(raw)
        body=re.sub(r"\s+"," ",raw[pos+1:end]).strip()
        choices.append({"no":no,"text":body})
    return choices


def parse_record(record, grade, source_file, answers):
    lines = record.pop("layoutLines")
    text_lines = [x["text"] for x in lines if x["text"]]
    raw = "\n".join(text_lines).strip()
    bracket = re.search(r"\[(20|21|22|23|24|29|30|31|32|33|34|35|36|37|38|39|40|41~42)\]", raw)
    source_label = bracket.group(1) if bracket else None
    source_nos = [41, 42] if source_label == "41~42" else ([int(source_label)] if source_label else [])
    source_no = "41-42" if source_label == "41~42" else (source_nos[0] if source_nos else None)
    # Instruction ends at the source-number bracket and can wrap over lines.
    instruction_parts, instruction_end = [], -1
    for idx,line in enumerate(text_lines):
        instruction_parts.append(line)
        if re.search(r"\[(?:20|21|22|23|24|29|30|31|32|33|34|35|36|37|38|39|40|41~42)\]", line):
            instruction_end=idx
            break
    instruction = " ".join(instruction_parts)
    body_text="\n".join(text_lines[instruction_end+1:]).strip()
    ptype = classify(instruction)
    choices = parse_choices(body_text)
    underlined=[]; last_mark=None
    for line in lines:
        words=line.get("allWords") or []
        mark_words=[w for w in words if any(c in CIRCLED for c in w["text"])]
        for box in line.get("underlineRects") or []:
            x0,_,x1,_=box
            targets=[w for w in words if x1>w["bbox"][0]+.3 and x0<w["bbox"][2]-.3]
            text=" ".join(w["text"] for w in targets)
            prior=[w for w in mark_words if w["bbox"][0]<x0]
            mark=next((c for c in (prior[-1]["text"] if prior else "") if c in CIRCLED),last_mark)
            if mark and re.search(r"[A-Za-z]",text) and not re.search(r"[가-힣]",text):
                item={"mark":mark,"text":text,"page":line["pdfPage"],
                      "wordBoxes":targets,"underlineRects":[box]}
                if underlined and underlined[-1]["mark"]==mark and underlined[-1]["page"]==item["page"]:
                    underlined[-1]["text"] += " " + text
                    underlined[-1]["wordBoxes"] += targets
                    underlined[-1]["underlineRects"].append(box)
                else: underlined.append(item)
        if mark_words:
            last_mark=next(c for c in reversed(mark_words[-1]["text"]) if c in CIRCLED)
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
        "type": ptype, "instruction": instruction, "bodyText":body_text,"rawText": raw,
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
        blank_rects=[]; representations=[]
        for line in lines:
            words=line.get("allWords") or []
            for box in line.get("underlineRects") or []:
                overlap=[w for w in words if max(0,min(box[2],w["bbox"][2])-max(box[0],w["bbox"][0]))
                         > .5*(w["bbox"][2]-w["bbox"][0])]
                placeholder=lambda t:not re.sub(r"[\s\u2000-\u200b().,;:A-B]","",t)
                if not overlap or all(placeholder(w["text"]) for w in overlap):
                    blank_rects.append({"page":line["pdfPage"],"bbox":box})
            if re.search(r"_{3,}|\u2003{2,}",line["text"]):
                representations.append({"page":line["pdfPage"],"lineText":line["text"]})
        result["blank"] = {"present": bool(blank_rects or representations),
                           "originalText": None, "rects":blank_rects,
                           "representations":representations}
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
        condition_match=re.search(r"(?:\[|<)?조건(?:\]|>)?\s*[:：]?\s*(.+)$",body_text,re.S)
        result["conditions"] = re.sub(r"\s+"," ",condition_match.group(1)).strip() if condition_match else None
        result["blanks"] = re.findall(r"\([A-Z]\)\s*(?:_+|\u2003+)?|ⓐ|ⓑ|_{3,}", body_text)
        after_source=re.split(r"\[(?:from|출처)[^\]]*\]",body_text,flags=re.I|re.S)[-1].strip()
        summary_match=re.search(r"(?:→|요약[^:：]*[:：])\s*(.+)$",body_text,re.S)
        result["summarySentence"] = re.sub(r"\s+"," ",(summary_match.group(1) if summary_match else after_source)).strip() or None
        result["targetText"] = after_source or body_text or None
    return result


def parse_answers(doc, answer_pages):
    text = "\n".join(doc[p-1].get_text("text", sort=False) for p in answer_pages)
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
    for key in ("grade", "sourceQuestionNo", "sourceQuestionNos", "variantIndex", "type",
                "instruction", "bodyText", "rawText", "rawLayout", "provenance"):
        if not r.get(key): errors.append(f"missing:{key}")
    if len(r.get("rawText", "")) < 150: reviews.append("rawText unusually short")
    if len(r.get("bodyText", "")) < 80: reviews.append("passage/body unusually short")
    provenance=r.get("provenance") or {}
    if not provenance.get("pdfPages"): errors.append("missing:provenance.pdfPages")
    source=r.get("sourceQuestionNo")
    marker="41~42" if source=="41-42" else str(source)
    if marker and f"[{marker}]" not in r.get("instruction",""):
        errors.append("source question marker mismatch")
    answer=r.get("answer")
    if not answer or not answer.get("raw"): reviews.append("answer missing")

    objective_types={"title_topic","blank","order","content_match","content_mismatch",
                     "implication","irrelevant_sentence","purpose","definition",
                     "comprehension","claim","reference","function","other"}
    if r["type"] in objective_types:
        choices=r.get("choices") or []
        if [x.get("no") for x in choices] != [1,2,3,4,5]: reviews.append("objective choices are not exactly ①-⑤")
        if any(not x.get("text","").strip() for x in choices): reviews.append("empty choice text")
        if any(any(c in x.get("text","") for c in CIRCLED) for x in choices): reviews.append("choice text contains duplicated circled number")
        if answer and answer.get("choice") not in range(1,6): reviews.append("objective answer is not 1-5")
    if r["type"] in ("grammar", "vocabulary"):
        marks = [x.get("mark") for x in r.get("testedSegments", []) if x.get("mark")]
        if "밑줄" in r["instruction"]:
            if set(marks)!=set(CIRCLED): reviews.append("tested marks are not exactly ①-⑤")
            for target in r.get("testedSegments",[]):
                if not all(target.get(k) for k in ("mark","text","page","wordBoxes","underlineRects")):
                    reviews.append("tested target association incomplete"); break
        elif [x.get("no") for x in (r.get("choices") or [])] != [1,2,3,4,5]:
            reviews.append("A/B/C grammar-vocabulary choices incomplete")
        if answer and answer.get("choice") not in range(1,6): reviews.append("grammar-vocabulary answer is not 1-5")
    if r["type"] == "blank":
        blank=r.get("blank",{})
        if not blank.get("present") or not (blank.get("rects") or blank.get("representations")):
            reviews.append("blank location/representation missing")
    if r["type"] == "order":
        blocks=r.get("blocks",{})
        if set(blocks) != set("ABC") or any(len(blocks.get(k,""))<20 for k in "ABC"):
            reviews.append("A/B/C extraction incomplete or too short")
        if answer and answer.get("choice") not in range(1,6): reviews.append("order answer is not 1-5")
    if r["type"] == "insertion":
        if not r.get("givenSentence"): reviews.append("given sentence extraction incomplete")
        points=r.get("insertionPoints",[])
        if len(points)!=5 or [x.get("no") for x in points] != [1,2,3,4,5]: reviews.append("insertion points are not exactly ①-⑤")
        if any(not all(x.get(k) is not None for k in ("page","line","bbox")) for x in points): reviews.append("insertion point location missing")
        if answer and answer.get("choice") not in range(1,6): reviews.append("insertion answer is not 1-5")
    if r["type"] == "summary":
        if not r.get("summarySentence"): reviews.append("summary sentence missing")
        if not r.get("blanks"): reviews.append("summary blank structure missing")
        if r.get("choices") and [x.get("no") for x in r["choices"]] != [1,2,3,4,5]: reviews.append("summary choices incomplete")
    if r["type"] == "written":
        if "조건" in r["instruction"] and not r.get("conditions"): reviews.append("written conditions missing")
        if not r.get("targetText"): reviews.append("written target missing")
    if errors: return "FAIL", errors + reviews
    if reviews: return "REVIEW", reviews
    return "PASS", []


def render_debug(records, target):
    cards=[]
    for r in records:
        body=[]
        for line in r["rawLayout"]:
            words=[]
            for w in line.get("allWords") or []:
                under=any(max(0,min(box[2],w["bbox"][2])-max(box[0],w["bbox"][0]))>.5*(w["bbox"][2]-w["bbox"][0]) for box in line.get("underlineRects") or [])
                words.append(f'<span class="{"u" if under else ""}">{html.escape(w["text"])}</span>')
            body.append(" ".join(words) if words else html.escape(line["text"]))
        answer=html.escape((r.get("answer") or {}).get("raw", ""))
        cards.append(f'<article><h2>{r["variantIndex"]}. [{r["sourceQuestionNo"]}] {r["type"]} - {r["validation"]["status"]}</h2><div class="box">'+"<br>".join(body)+f'</div><details><summary>정답·해설</summary>{answer}</details></article>')
    target.write_text('<!doctype html><meta charset="utf-8"><style>body{font:14px Arial;margin:24px}article{break-inside:avoid;margin:0 0 32px}.box{border:1px solid #bbb;padding:16px;line-height:1.55}h2{font-size:16px}.u{text-decoration:underline;text-underline-offset:2px}details{margin-top:8px;color:#444}</style>'+"".join(cards), encoding="utf-8")


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
    actual=[r["variantIndex"] for r in records]
    expected=list(range(1,len(records)+1))
    if actual != expected:
        for r in records:
            r.setdefault("sequenceErrors",[]).append("variantIndex sequence has duplicate or gap")
    for r in records:
        status, reasons=validate(r)
        if r.get("sequenceErrors"): status,reasons="FAIL",reasons+r["sequenceErrors"]
        r["validation"]={"status":status,"reasons":reasons}
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
