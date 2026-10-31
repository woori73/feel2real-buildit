"""One-time, user-approved server browser sign-in. Never requests a password.

Runs as ec2-user with Chromium sandbox enabled. Authentication state remains
inside this server's private profile. No cookie export and no public listener.
"""
import datetime as dt
import json
import os
from pathlib import Path
import time
from playwright.sync_api import sync_playwright

ROOT=Path('/home/ec2-user/f2r-cafe-browser')
STUDIO='https://studio.cafe.naver.com/@31312978/member/list'


def record(page,state):
    page.screenshot(path=str(ROOT/'auth-screen.png'))
    (ROOT/'auth-screen.png').chmod(0o600)
    (ROOT/'auth-status.json').write_text(json.dumps({
        'state':state,'updated_at':dt.datetime.now(dt.timezone.utc).isoformat()}))


def main():
    os.umask(0o077)
    ROOT.mkdir(mode=0o700,parents=True,exist_ok=True)
    ROOT.chmod(0o700)
    with sync_playwright() as engine:
        context=engine.chromium.launch_persistent_context(
            str(ROOT/'profile'),headless=True,chromium_sandbox=True,
            viewport={'width':1000,'height':850},locale='ko-KR')
        page=context.pages[0] if context.pages else context.new_page()
        page.goto(STUDIO,wait_until='domcontentloaded',timeout=60000)
        page.wait_for_timeout(2000)
        # Only normal visible authentication UI is used; challenges are not solved.
        qr=page.get_by_text('QR코드',exact=True)
        if qr.count()==1 and qr.is_visible():
            qr.click()
        record(page,'USER_LOGIN_REQUIRED')
        for _ in range(120):
            page.wait_for_timeout(5000)
            if 'studio.cafe.naver.com' in page.url:
                manager=page.get_by_role('link',name='지피지기자산 내 정보',exact=True)
                members=page.get_by_role('heading',name='전체 멤버 관리',exact=True)
                if manager.count()==1 and members.count()==1:
                    record(page,'MANAGER_SESSION_READY')
                    context.close()
                    print('MANAGER_SESSION_READY')
                    return
                dialog=page.get_by_role('alertdialog',name='본인 인증 안내')
                if dialog.count()==1:
                    record(page,'USER_CAFE_IDENTITY_AUTH_REQUIRED')
                    # The user must complete any additional identity challenge.
                    continue
            record(page,'USER_LOGIN_REQUIRED')
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
