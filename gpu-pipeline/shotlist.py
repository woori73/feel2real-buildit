"""대본(screenplay JSON) -> 샷 리스트 변환·검증. GPU 없이 실행되며, 이후 ComfyUI 작업 생성의 입력이 된다."""
import json
from typing import Any

MAX_SHOT_SECONDS = 8.0   # 이미지→영상 모델 1회 생성 길이 상한(보수적 가정, GPU 확정 후 조정)
CHARS_PER_SECOND = {"ko": 6.0, "en": 14.0}  # 말하기 속도 추정(자/초)


class ScriptError(ValueError):
    pass


def estimate_seconds(text: str, lang: str) -> float:
    if lang not in CHARS_PER_SECOND:
        raise ScriptError(f"지원하지 않는 언어: {lang}")
    return max(1.0, round(len(text.strip()) / CHARS_PER_SECOND[lang], 1))


def validate_script(script: dict[str, Any]) -> None:
    for key in ("title", "lang", "characters", "scenes"):
        if key not in script:
            raise ScriptError(f"필수 키 없음: {key}")
    ids = {c["id"] for c in script["characters"]}
    if len(ids) != len(script["characters"]):
        raise ScriptError("캐릭터 id 중복")
    for scene in script["scenes"]:
        for line in scene.get("lines", []):
            if line["speaker"] not in ids:
                raise ScriptError(f"정의되지 않은 화자: {line['speaker']}")


def build_shotlist(script: dict[str, Any]) -> list[dict[str, Any]]:
    """대사 1줄 = 1샷. 길이가 상한을 넘으면 같은 장면 안에서 분할한다."""
    validate_script(script)
    lang = script["lang"]
    shots: list[dict[str, Any]] = []
    for si, scene in enumerate(script["scenes"], start=1):
        for li, line in enumerate(scene.get("lines", []), start=1):
            seconds = estimate_seconds(line["text"], lang)
            parts = max(1, int(-(-seconds // MAX_SHOT_SECONDS)))
            for pi in range(parts):
                shots.append({
                    "shot_id": f"S{si:02d}L{li:02d}P{pi + 1}",
                    "scene": si,
                    "setting": scene.get("setting", ""),
                    "speaker": line["speaker"],
                    "text": line["text"] if parts == 1 else f"({pi + 1}/{parts}) {line['text']}",
                    "seconds": round(seconds / parts, 1),
                    "visual": line.get("visual", ""),
                })
    return shots


def total_seconds(shots: list[dict[str, Any]]) -> float:
    return round(sum(s["seconds"] for s in shots), 1)


def load_script(path: str) -> dict[str, Any]:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


if __name__ == "__main__":
    import sys
    shots = build_shotlist(load_script(sys.argv[1]))
    print(json.dumps(shots, ensure_ascii=False, indent=2))
    print(f"shots={len(shots)} total_seconds={total_seconds(shots)}", file=sys.stderr)
