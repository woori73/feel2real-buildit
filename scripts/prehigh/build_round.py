# -*- coding: utf-8 -*-
"""실전 독해(18~45번) 회차 자료 만들기: 내용 JSON → 문항 그림(webp)·근거 좌표·문제지 PDF·round.json.

사용법은 README.md. 표준 라이브러리와 PIL(Pillow), 크롬(헤드리스)만 쓴다.
"""
import argparse
import json
import os
import pathlib
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import prehigh_round as lib  # noqa: E402

DEFAULT_CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'


def run_chrome(chrome, profile, *args):
    """크롬을 헤드리스로 한 번 실행하고 표준 출력을 돌려준다. 프로필을 따로 써서 열려 있는 크롬과 섞이지 않게 한다."""
    command = [chrome, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
               '--no-first-run', '--disable-extensions', f'--user-data-dir={profile}', *args]
    done = subprocess.run(command, capture_output=True, timeout=180)
    if done.returncode != 0:
        raise RuntimeError(f'크롬 실행 실패({done.returncode}): {done.stderr.decode("utf-8", "replace")[-500:]}')
    return done.stdout.decode('utf-8', 'replace')


def shoot(chrome, profile, url, chunk, out, png):
    """그림 한 묶음(위에서 아래로 이어 붙인 화면)을 한 번에 찍고, 재어 둔 위치대로 잘라 webp로 저장한다."""
    total = sum(height for _, _, height in chunk)
    run_chrome(chrome, profile, f'--window-size={lib.FIG_WIDTH},{total}', f'--screenshot={png}',
               url + '#' + ','.join(name for name, _, _ in chunk))
    with Image.open(png) as shot:
        if shot.width < lib.FIG_WIDTH or shot.height < total:
            raise RuntimeError(f'찍힌 그림({shot.width}x{shot.height})이 필요한 크기({lib.FIG_WIDTH}x{total})보다 작습니다.')
        page = shot.convert('RGB')
        for name, top, height in chunk:
            page.crop((0, top, lib.FIG_WIDTH, top + height)).save(out / f'{name}.webp', 'WEBP', quality=78, method=6)


def draw_check(image_path, marks, part, target):
    """확인용 그림: 문항 그림 위에 근거(노랑)·정답(파랑) 사각형을 덧그린다."""
    with Image.open(image_path) as image:
        base = image.convert('RGBA')
    layer = Image.new('RGBA', base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for mark in (m for m in marks if m['part'] == part):
        box = [mark['x'] * base.width / 10000, mark['y'] * base.height / 10000,
               (mark['x'] + mark['width']) * base.width / 10000, (mark['y'] + mark['height']) * base.height / 10000]
        if mark['kind'] == 'evidence':
            draw.rectangle(box, fill=(255, 221, 0, 90))
        else:
            draw.rectangle(box, outline=(0, 90, 255, 255), width=3)
    Image.alpha_composite(base, layer).convert('RGB').save(target)


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description='실전 독해 회차 자료(그림·좌표·문제지 PDF·round.json) 만들기')
    parser.add_argument('content', help='내용 JSON(round_content.json 형식)')
    parser.add_argument('out', help='결과 폴더(예: src/main/resources/prehigh-part6/r01)')
    parser.add_argument('--number', type=int, help='회차 번호(없으면 내용 JSON의 number)')
    parser.add_argument('--grade', default='고2', help='문제지 표지의 학년 표시(기본 고2)')
    parser.add_argument('--chrome', default=os.environ.get('PREHIGH_CHROME', DEFAULT_CHROME), help='크롬 실행 파일(환경변수 PREHIGH_CHROME)')
    parser.add_argument('--check-dir', help='좌표 확인용 PNG를 둘 폴더(저장소 밖)')
    args = parser.parse_args()

    content = json.loads(pathlib.Path(args.content).read_text(encoding='utf-8'))
    number = args.number or content['number']
    out = pathlib.Path(args.out).resolve()  # 크롬에 넘기는 경로는 절대 경로여야 한다
    out.mkdir(parents=True, exist_ok=True)

    figures_page = lib.figures_page(content)
    paper_page = lib.paper_page(content, number, args.grade)
    banned = lib.banned_words(lib.visible_text(figures_page) + ' ' + lib.visible_text(paper_page), content.get('source', ''))
    if banned:
        sys.exit(f'문제지·그림 글자에 쓰면 안 되는 낱말이 있습니다: {banned}')

    with tempfile.TemporaryDirectory() as work:
        work = pathlib.Path(work)
        (work / 'figures.html').write_text(figures_page, encoding='utf-8')
        (work / 'paper.html').write_text(paper_page, encoding='utf-8')
        url, profile = (work / 'figures.html').as_uri(), work / 'profile'

        figures = lib.parse_measure(run_chrome(args.chrome, profile, '--virtual-time-budget=3000', '--dump-dom', url))
        for chunk in lib.chunk_figures([(name, round(figure['h'])) for name, figure in figures.items()]):
            shoot(args.chrome, profile, url, chunk, out, work / 'shot.png')
        run_chrome(args.chrome, profile, '--no-pdf-header-footer', f'--print-to-pdf={out / "paper.pdf"}',
                   (work / 'paper.html').as_uri())
        if not (out / 'paper.pdf').is_file():
            sys.exit('paper.pdf가 만들어지지 않았습니다.')

    data = lib.assemble_round(content, figures, number)
    problems = lib.validate_round(data)
    if problems:
        sys.exit('round.json 검증 실패:\n' + '\n'.join(problems))
    (out / 'round.json').write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')

    if args.check_dir:
        check = pathlib.Path(args.check_dir)
        check.mkdir(parents=True, exist_ok=True)
        for i, item in enumerate(data['reading']):
            n = lib.FIRST_NUMBER + i
            draw_check(out / item['image'], item['marks'], 'question', check / f'q{n}.png')
            if item['passage']:
                draw_check(out / item['passage'], item['marks'], 'passage', check / f'q{n}-passage.png')
    print(f'{number}회: 그림 {len(figures)}장, 표시 {sum(len(item["marks"]) for item in data["reading"])}개, paper.pdf, round.json → {out}')


if __name__ == '__main__':
    main()
