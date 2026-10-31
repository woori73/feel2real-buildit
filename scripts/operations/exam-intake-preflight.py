#!/usr/bin/env python3
"""Read-only host evidence. Reuses the existing private MySQL credential adapter.
Run on the selected production host, never point it at an unrelated environment.
Prints schema/flags/readiness only; no account, contact, hostname, password or file content.
"""
import argparse, importlib.util, json, os, re, stat, sys
from pathlib import Path

TABLES = tuple('exam_intake_' + n for n in ('request','document','action','delivery','event','bank','bank_audit','guardian_consent'))
spec = importlib.util.spec_from_file_location('host', Path(__file__).with_name('a1-precheck.py'))
host = importlib.util.module_from_spec(spec); spec.loader.exec_module(host)
SQL = """SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT 'history',version,checksum,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT 'column',TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (%s) ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT 'index',TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (%s) ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
ROLLBACK;
""" % (','.join("'"+t+"'" for t in TABLES), ','.join("'"+t+"'" for t in TABLES))


def schema(env_file):
    captured = []
    def runner(args, **kwargs):
        value = host.run(args, **kwargs)
        captured.append(value)
        return value
    old = host.SQL
    try:
        host.SQL = SQL
        host.database(env_file, runner=runner)
    finally:
        host.SQL = old
    if not captured or captured[0] is None:
        return {'status':'unavailable'}
    result={'status':'ok','history':[], 'columns':[], 'indexes':[]}
    for line in captured[0].splitlines():
        f=line.split('\t')
        if f[0]=='history' and len(f)==4 and (re.fullmatch(r'\d+', f[1]) or f[1]=='NULL') and re.fullmatch(r'-?\d+|NULL', f[2]) and f[3] in ('0','1'):
            result['history'].append({'version':int(f[1]) if f[1]!='NULL' else None, 'checksum':int(f[2]) if f[2]!='NULL' else None, 'success':f[3]=='1'})
        elif f[0] in ('column','index') and f[1] in TABLES and all(re.fullmatch(r'[A-Za-z0-9_(), ]+', v) for v in f[2:]):
            result['columns' if f[0]=='column' else 'indexes'].append(f[1:])
        else:
            return {'status':'invalid-response'}
    if not result['history']: return {'status':'history-unavailable'}
    result['presentTables']=sorted({c[0] for c in result['columns']})
    return result


def runtime(pid):
    if not pid:return {'status':'unavailable'}
    try:
        values=dict(x.split('=',1) for x in Path(f'/proc/{pid}/environ').read_text().split('\0') if '=' in x)
        args=Path(f'/proc/{pid}/cmdline').read_text().split('\0')
    except (OSError,ValueError):return {'status':'unavailable'}
    result={'status':'observed-process-environment','flags':{}}
    for name in ('enabled','bank-public-enabled','bank-admin-enabled','email-notifications-enabled'):
        key='APP_EXAM_INTAKE_'+name.upper().replace('-','_')
        value=values.get(key)
        for arg in args:
            if arg.startswith('--app.exam-intake.'+name+'='):value=arg.split('=',1)[1]
        result['flags'][name]=value.lower() if value and value.lower() in ('true','false') else 'unset-or-unknown'
    result['additionalPropertySources']=any(k in values for k in ('SPRING_APPLICATION_JSON','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS')) or any(a.startswith(('--spring.config.','-Dapp.exam-intake.')) for a in args)
    result['policyVersionsMatch']={key: values.get('APP_EXAM_INTAKE_'+key.upper().replace('-','_'))==expected for key,expected in [('retention-reviewed-policy-version','exam-privacy-20261030-v1'),('refund-reviewed-policy-version','exam-terms-20261030-v1')]}
    result['mailConfigured']={key: bool(values.get(key)) for key in ('SPRING_MAIL_HOST','SPRING_MAIL_USERNAME','SPRING_MAIL_PASSWORD','APP_EXAM_INTAKE_NOTIFICATION_RECIPIENT')}
    private=values.get('APP_EXAM_INTAKE_PRIVATE_DIRECTORY',''); scanner=values.get('APP_EXAM_INTAKE_SCANNER_EXECUTABLE','')
    try:
        p=Path(private)
        result['privateDirectory']={'configured':bool(private),'absolute':p.is_absolute(),'exists':bool(private) and p.is_dir(),'symlink':p.is_symlink(),'mode0700':bool(private) and stat.S_IMODE(p.stat().st_mode)==0o700}
    except OSError:result['privateDirectory']={'configured':bool(private),'accessible':False}
    result['scanner']={'configured':bool(scanner),'executable':bool(scanner) and os.path.isabs(scanner) and os.access(scanner,os.X_OK),'engineAndDefinitionsVerified':False}
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--env-file',default='/etc/f2r/env');a=p.parse_args()
    state,pid=host.systemd('f2r-app.service')
    report={'schema':schema(a.env_file),'service':state,'runtime':runtime(pid),'readOnly':True}
    print(json.dumps(report,ensure_ascii=False,indent=2))
    sys.exit(0 if report['schema']['status']=='ok' else 2)
