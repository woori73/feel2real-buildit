"""Append verified cafe thanks observations to the existing NewStart roster.

No account linking, entitlement granting, historical update or deletion.
"""
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

CAFE_URL = 'https://studio.cafe.naver.com/@31312978/member/list'


def validate(observation):
    if observation.get('grade') != '감사멤버' or observation.get('member_level') != 150:
        raise ValueError('VERIFIED_THANKS_GRADE_REQUIRED')
    if not observation.get('verified_by') or observation.get('cafe_id') != 31312978:
        raise ValueError('VERIFIED_CAFE_OBSERVATION_REQUIRED')
    if not re.fullmatch('[a-z0-9]{12}', observation.get('member_tag', '')):
        raise ValueError('STUDIO_MEMBER_TAG_REQUIRED')
    if not re.fullmatch('[A-Za-z0-9_-]{1,20}', observation.get('naver_id_prefix', '')):
        raise ValueError('OBSERVED_ID_PREFIX_REQUIRED')
    nickname = observation.get('nickname', '').strip()
    if not nickname or len(nickname) > 50 or '\n' in nickname or '\r' in nickname:
        raise ValueError('VALID_OBSERVED_NICKNAME_REQUIRED')
    when = dt.datetime.fromisoformat(observation['observed_at'].replace('Z', '+00:00'))
    if when.tzinfo is None:
        raise ValueError('OBSERVATION_TIMEZONE_REQUIRED')
    if abs((dt.datetime.now(dt.timezone.utc)-when).total_seconds()) > 86400:
        raise ValueError('FRESH_OBSERVATION_REQUIRED')
    changed = observation.get('grade_changed_at')
    if changed:
        dt.date.fromisoformat(changed)
    return {**observation, 'nickname': nickname}


def sql_text(value):
    if value is None:
        return 'NULL'
    return "CONVERT(0x" + str(value).encode('utf-8').hex() + " USING utf8mb4) COLLATE utf8mb4_unicode_ci"


def statement(raw):
    item = validate(raw)
    tag = sql_text(item['member_tag'])
    nick = sql_text(item['nickname'])
    prefix = sql_text(item['naver_id_prefix'].lower())
    date = dt.datetime.fromisoformat(item['observed_at'].replace('Z', '+00:00')).astimezone(
        dt.timezone(dt.timedelta(hours=9))).date().isoformat()
    # Refuse a nickname/prefix collision with an older MANUAL/random-tag row instead of
    # silently changing or duplicating the record used by account-claiming logic.
    return f"""SET NAMES utf8mb4;
SET @locked=GET_LOCK(CONCAT('cafe-roster:',{tag}),10);
START TRANSACTION;
SET @tag_exists=(SELECT COUNT(*) FROM newstart_thanks_roster WHERE cafe_member_tag={tag});
SET @nick_exists=(SELECT COUNT(*) FROM newstart_thanks_roster WHERE nickname={nick} AND naver_id_prefix={prefix});
INSERT INTO newstart_thanks_roster
 (cafe_member_tag,naver_id_prefix,nickname,grade,grade_changed_at,member_level,source,
  snapshot_date,source_url,source_file,match_status,linked_user_id,linked_provider_user_id,created_by,updated_by)
SELECT {tag},{prefix},{nick},'감사멤버',{sql_text(item.get('grade_changed_at'))},150,'NAVER_CAFE',
 {sql_text(date)},{sql_text(CAFE_URL)},{sql_text('runtime:cafe-confirmed:'+item['member_tag'])},
 'UNMATCHED',NULL,NULL,'CAFE_AUTOMATION','CAFE_AUTOMATION'
WHERE @locked=1 AND @tag_exists=0 AND @nick_exists=0;
SELECT 'ROSTER',roster_id FROM newstart_thanks_roster WHERE cafe_member_tag={tag} ORDER BY roster_id LIMIT 1;
SELECT 'RESULT',IF(@locked<>1,'LOCK_FAILED',IF(@tag_exists=0 AND @nick_exists>0,'IDENTITY_REVIEW','OK'));
COMMIT;
DO RELEASE_LOCK(CONCAT('cafe-roster:',{tag}));
"""


def run_mysql(sql, env_file='/etc/f2r/env', credential_helper='/opt/f2r-ebook/a1-precheck.py'):
    spec = importlib.util.spec_from_file_location('private_db_adapter', credential_helper)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    values = helper.credentials(env_file)
    settings = {'host': values['DB_HOST'], 'user': values.get('DB_USER') or values['DB_USERNAME'],
                'password': values['DB_PASSWORD'], 'database': values.get('DB_NAME') or 'f2r_db',
                'port': values.get('DB_PORT') or '3306'}
    with tempfile.TemporaryDirectory(prefix='f2r-roster-', dir='/dev/shm') as directory:
        os.chmod(directory, 0o700)
        config = Path(directory)/'client.cnf'
        fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as output:
            output.write('[client]\n'+''.join(k+'='+helper.cnf_quote(v)+'\n' for k,v in settings.items()))
        client = '/usr/bin/mysql' if Path('/usr/bin/mysql').is_file() else '/usr/bin/mariadb'
        result = subprocess.run([client, '--defaults-file='+str(config), '--batch',
                                 '--skip-column-names', '--connect-timeout=10'],
                                input=sql, capture_output=True, text=True, timeout=45,
                                env={k:v for k,v in os.environ.items()
                                     if k not in ('MYSQL_PWD', 'MYSQL_TEST_LOGIN_FILE')})
        if result.returncode:
            raise RuntimeError('ROSTER_DB_OPERATION_FAILED')
        return result.stdout


def sync(observation, execute=run_mysql):
    rows = execute(statement(observation)).splitlines()
    result = next((r.split('\t')[1] for r in rows if r.startswith('RESULT\t')), None)
    roster = next((int(r.split('\t')[1]) for r in rows if re.fullmatch(r'ROSTER\t[0-9]+', r)), None)
    if result != 'OK' or roster is None:
        raise RuntimeError('ROSTER_SYNC_REQUIRES_REVIEW')
    return roster


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('observation', type=Path)
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        value = json.loads(args.observation.read_text())
        if args.dry_run:
            statement(value)
            print(json.dumps({'valid': True, 'database_written': False}))
        else:
            print(json.dumps({'roster_id': sync(value), 'synced': True}))
    except Exception as error:
        print(json.dumps({'synced': False, 'error_type': type(error).__name__}))
        raise SystemExit(1)
