"""Visible Studio UI adapter for a single paid order's exact Naver ID.

Authentication files and receipts remain private on the server. Session expiry,
ambiguous results and UI changes stop the order; they never unlock email.
"""
import argparse
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import re
import time
from playwright.sync_api import sync_playwright

ROOT = Path(os.environ.get('F2R_CAFE_BROWSER_ROOT', '/home/ec2-user/f2r-cafe-browser'))
STUDIO = 'https://studio.cafe.naver.com/@31312978/member/list'


def member_result(page, identity):
    page.get_by_role('button', name='멤버 검색', exact=True).click()
    dialog = page.get_by_role('dialog')
    dialog.get_by_role('button', name='아이디', exact=True).click()
    dialog.get_by_label('아이디 검색어', exact=True).fill(identity)
    dialog.get_by_role('button', name='멤버 검색', exact=True).click()
    dialog.wait_for(state='hidden', timeout=20000)
    deadline = time.monotonic()+20
    while time.monotonic() < deadline:
        text = page.locator('body').inner_text()
        match = re.search(r'‘'+re.escape(identity)+r'’\s*검색 결과\s*([0-9,]+)명', text)
        if match:
            if int(match[1].replace(',', '')) != 1:
                raise ValueError('EXACT_UNIQUE_MEMBER_REQUIRED')
            break
        page.wait_for_timeout(250)
    else:
        raise ValueError('SEARCH_COMPLETION_REQUIRED')
    tags = page.get_by_role('button', name=re.compile(r'^멤버태그 [a-z0-9]{12} 복사$')).filter(visible=True)
    if tags.count() != 1:
        raise ValueError('UNIQUE_MEMBER_TAG_REQUIRED')
    tag_button = tags.first
    lines = tag_button.evaluate('(e)=>e.parentElement.parentElement.innerText').splitlines()
    lines = [line.strip() for line in lines if line.strip()]
    if len(lines) != 4:
        raise ValueError('OBSERVED_MEMBER_ROW_REQUIRED')
    nickname, masked, tag, grade = lines
    prefix_match = re.fullmatch(r'\(([a-zA-Z0-9_-]{1,4})\*+\)', masked)
    if not prefix_match or not identity.startswith(prefix_match[1].lower()):
        raise ValueError('OBSERVED_ID_MISMATCH')
    if not re.fullmatch(r'[a-z0-9]{12}', tag):
        raise ValueError('MEMBER_TAG_REQUIRED')
    return {'cafe_id': 31312978, 'searched_id': identity, 'result_count': 1,
            'nickname': nickname, 'naver_id_prefix': prefix_match[1].lower(),
            'member_tag': tag, 'grade': grade}


def run(identity, upgrade=False):
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{4,19}', identity):
        raise ValueError('FULL_NAVER_ID_REQUIRED')
    os.umask(0o077)
    with (ROOT/'member-ui.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        profile_config = json.loads((ROOT/'runtime.json').read_text())
        profile = ROOT/profile_config['profile']
        if profile.parent != ROOT:
            raise ValueError('PRIVATE_PROFILE_REQUIRED')
        with sync_playwright() as engine:
            context = engine.chromium.launch_persistent_context(
                str(profile), headless=True, chromium_sandbox=True,
                viewport={'width':1000,'height':850}, locale='ko-KR')
            manager_ready = False
            try:
                context.add_cookies(json.loads((ROOT/'manager-session.json').read_text())['cookies'])
                page = context.pages[0] if context.pages else context.new_page()
                page.goto(STUDIO, wait_until='domcontentloaded', timeout=45000)
                page.get_by_role('heading', name='전체 멤버 관리', exact=True).wait_for(
                    state='visible', timeout=25000)
                page.get_by_role('link', name='지피지기자산 내 정보', exact=True).wait_for(
                    state='visible', timeout=10000)
                if page.get_by_role('alertdialog').count():
                    raise ValueError('MANAGER_IDENTITY_AUTH_REQUIRED')
                manager_ready = True
                receipt = member_result(page, identity)
                if receipt['grade'] != '감사멤버':
                    if not upgrade or receipt['grade'] not in ('새싹멤버','일반멤버','성실멤버','모범멤버','골든멤버'):
                        raise ValueError('GRADE_REVIEW_REQUIRED')
                    page.get_by_role('checkbox', name=receipt['nickname']+' 선택', exact=True).check()
                    page.get_by_role('button', name='등급 변경', exact=True).filter(visible=True).first.click()
                    dialog = page.get_by_role('dialog')
                    dialog.get_by_role('combobox').click()
                    page.get_by_role('option', name='감사멤버', exact=True).click()
                    reason = dialog.get_by_role('textbox')
                    if reason.count() == 1:
                        reason.fill('전자책 구매 확인 감사멤버 등업')
                    dialog.get_by_role('button', name='확인', exact=True).click()
                    dialog.wait_for(state='hidden', timeout=20000)
                    page.reload(wait_until='domcontentloaded', timeout=45000)
                    page.get_by_role('heading', name='전체 멤버 관리', exact=True).wait_for(
                        state='visible', timeout=25000)
                    receipt = member_result(page, identity)
                if receipt['grade'] != '감사멤버':
                    raise ValueError('THANKS_GRADE_NOT_CONFIRMED')
                receipt.update(member_level=150, verified_by='CAFE_UI_RUNTIME',
                    observed_at=dt.datetime.now(dt.timezone.utc).isoformat())
                page.screenshot(path=str(ROOT/'last-member-check.png'))
                return receipt
            finally:
                if manager_ready:
                    context.storage_state(path=str(ROOT/'manager-session.json'))
                context.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--lookup-id', required=True)
    parser.add_argument('--upgrade', action='store_true')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.lookup_id, args.upgrade), ensure_ascii=False))
    except Exception as error:
        print(json.dumps({'ok':False,'error_type':type(error).__name__}))
        raise SystemExit(1)
