"""Flyway migration SQL(INSERT ... VALUES)에서 tp_exams·tp_questions 행을 읽는다. DB에 접속하지 않는다."""
import re
from pathlib import Path


def _values(text, start):
    """text[start]가 '(' 인 튜플 하나를 읽어 (값 목록, 끝 위치)를 돌려준다."""
    assert text[start] == "("
    _EMPTY = object()
    i, vals, cur, n = start + 1, [], _EMPTY, len(text)
    while i < n:
        c = text[i]
        if c == "'":
            buf, i = [], i + 1
            while True:
                c = text[i]
                if c == "\\":
                    nxt = text[i + 1]
                    buf.append({"n": "\n", "t": "\t", "r": "\r", "0": "\0"}.get(nxt, nxt))
                    i += 2
                elif c == "'" and text[i + 1:i + 2] == "'":
                    buf.append("'")
                    i += 2
                elif c == "'":
                    i += 1
                    break
                else:
                    buf.append(c)
                    i += 1
            cur = "".join(buf)
        elif c in ", \n\t\r":
            if c == ",":
                if cur is _EMPTY:
                    raise ValueError("empty value")
                vals.append(cur)
                cur = _EMPTY
            i += 1
        elif c == ")":
            if cur is not _EMPTY:
                vals.append(cur)
            return vals, i + 1
        else:
            m = re.match(r"[^,)\s]+", text[i:])
            tok = m.group(0)
            cur = None if tok.upper() == "NULL" else (int(tok) if re.fullmatch(r"-?\d+", tok) else tok)
            i += len(tok)
    raise ValueError("unterminated tuple")


def inserts(path, table):
    text = Path(path).read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r"INSERT INTO %s \(([^)]*)\) VALUES" % re.escape(table), text):
        cols = [c.strip() for c in m.group(1).split(",")]
        i = m.end()
        while True:
            while text[i] in " \n\t\r":
                i += 1
            if text[i] != "(":
                break
            vals, i = _values(text, i)
            if len(vals) != len(cols):
                raise ValueError(f"{path}: {len(vals)} values for {len(cols)} columns")
            out.append(dict(zip(cols, vals)))
            while text[i] in " \n\t\r":
                i += 1
            if text[i] == ",":
                i += 1
                continue
            break
    return out


def exam_blocks(path):
    """시험(exam_code)별 tp_exams 행과 그 시험의 tp_questions 행을 묶는다(@변수로 연결)."""
    text = Path(path).read_text(encoding="utf-8")
    exams = {e["exam_code"]: e for e in inserts(path, "tp_exams")}
    var_to_code = dict(re.findall(r"SET (@\w+) = \(SELECT exam_id FROM tp_exams WHERE exam_code = '([^']+)'\)", text))
    blocks = {code: {"exam": e, "questions": []} for code, e in exams.items()}
    for q in inserts(path, "tp_questions"):
        code = var_to_code.get(q["exam_id"])
        if code not in blocks:
            raise ValueError(f"{path}: unknown exam variable {q['exam_id']}")
        blocks[code]["questions"].append(q)
    return blocks
