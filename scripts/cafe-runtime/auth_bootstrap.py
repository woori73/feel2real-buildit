"""One-time, user-approved server browser sign-in. Never requests a password.

Runs as ec2-user with Chromium sandbox enabled. Authentication state remains
inside this server's private profile. No cookie export and no public listener.
"""
import datetime as dt
import json
import os
from pathlib import Path
import time
import re
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright

ROOT=Path('/home/ec2-user/f2r-cafe-browser')
STUDIO='https://studio.cafe.naver.com/@31312978/member/list'


def record(page,state):
    session=ROOT/'manager-session.json'
    page.context.storage_state(path=str(session))
    session.chmod(0o600)
    action=ROOT/'auth-action.json'
    if action.exists():
        item=json.loads(action.read_text())
        action.unlink()
        if item.get('role') in ('button','link') and item.get('name') in ('알림 다시 받기','확인'):
            target=page.get_by_role(item['role'],name=item['name'],exact=True)
            if target.count()==1 and target.is_visible():
                target.click()
                page.wait_for_timeout(1500)
    if state=='USER_CAFE_IDENTITY_AUTH_REQUIRED':
        certificate=page.get_by_text('네이버 인증서',exact=True)
        if '인증 수단을 선택' in page.locator('body').inner_text() and certificate.count()==1 and certificate.is_visible():
            certificate.click()
            page.wait_for_timeout(1500)
    page.screenshot(path=str(ROOT/'auth-screen.png'))
    (ROOT/'auth-screen.png').chmod(0o600)
    (ROOT/'auth-status.json').write_text(json.dumps({
        'state':state,'updated_at':dt.datetime.now(dt.timezone.utc).isoformat()}))


def main():
    os.umask(0o077)
    ROOT.mkdir(mode=0o700,parents=True,exist_ok=True)
    ROOT.chmod(0o700)
    config=json.loads((ROOT/'runtime.json').read_text())
    profile=ROOT/config['profile']
    if profile.parent!=ROOT:
        raise ValueError('PRIVATE_PROFILE_REQUIRED')
    with sync_playwright() as engine:
        context=engine.chromium.launch_persistent_context(
            str(profile),headless=True,chromium_sandbox=True,
            viewport={'width':1000,'height':850},locale='ko-KR')
        page=context.pages[0] if context.pages else context.new_page()
        saved=ROOT/'manager-session.json'
        if saved.exists():
            context.add_cookies(json.loads(saved.read_text()).get('cookies',[]))
        page.goto('https://nid.naver.com/nidlogin.login',wait_until='domcontentloaded',timeout=60000)
        page.wait_for_timeout(2000)
        # Only normal visible authentication UI is used; challenges are not solved.
        qr=page.get_by_role('link',name=re.compile(r'QR.*로그인'))
        if qr.count()==1 and qr.is_visible():
            qr.click()
        record(page,'USER_LOGIN_REQUIRED')
        auth_opened=False
        for _ in range(360):
            page.wait_for_timeout(5000)
            if urlsplit(page.url).hostname=='www.naver.com':
                page.goto(STUDIO,wait_until='domcontentloaded',timeout=60000)
            if urlsplit(page.url).hostname=='studio.cafe.naver.com':
                manager=page.get_by_role('link',name='지피지기자산 내 정보',exact=True)
                members=page.get_by_role('heading',name='전체 멤버 관리',exact=True)
                if manager.count()==1 and members.count()==1:
                    record(page,'MANAGER_SESSION_READY')
                    context.close()
                    print('MANAGER_SESSION_READY')
                    return
                dialog=page.get_by_role('alertdialog',name='본인 인증 안내')
                if dialog.count()==1:
                    if not auth_opened:
                        dialog.get_by_role('button',name='확인',exact=True).click()
                        auth_opened=True
                        page.wait_for_timeout(2000)
                    active=next((p for p in reversed(context.pages) if not p.is_closed()),page)
                    record(active,'USER_CAFE_IDENTITY_AUTH_REQUIRED')
                    # The user must complete any additional identity challenge.
                    continue
            active=next((p for p in reversed(context.pages) if not p.is_closed()),page)
            record(active,'USER_CAFE_IDENTITY_AUTH_REQUIRED' if auth_opened else 'USER_LOGIN_REQUIRED')
        context.close()
        print('USER_AUTH_TIMEOUT')


if __name__=='__main__':
    try:main()
    except Exception as error:
        ROOT.mkdir(mode=0o700,parents=True,exist_ok=True)
        (ROOT/'auth-status.json').write_text(json.dumps({'state':'BOOTSTRAP_FAILED',
            'error_type':type(error).__name__}))
        print('AUTH_BOOTSTRAP_FAILED',type(error).__name__)
        raise SystemExit(1)
