"""Generate deterministic CloudFormation; existing MediaAudit is NEVER a resource."""
import ast
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
ACCOUNT = '425805163960'
INSTANCE = 'i-0414de33d2803c723'
REGION = 'ap-northeast-2'
SUBJECT = 'repo:woori73@299170593/feel2real-buildit@1340651018:ref:refs/heads/main'


def host_source(entry):
    functions, declarations = {}, []
    for name in ('release_ops.py', 'overlay.py', 'host_operations.py'):
        text = (HERE/name).read_text()
        for node in ast.parse(text).body:
            code = ast.get_source_segment(text, node)
            if isinstance(node, ast.FunctionDef): functions[node.name] = (node, code)
            elif isinstance(node, (ast.Import, ast.ImportFrom, ast.Assign)): declarations.append(code)
    selected, pending = set(), [entry]
    while pending:
        name = pending.pop()
        if name in selected: continue
        selected.add(name)
        pending.extend(n.id for n in ast.walk(functions[name][0]) if isinstance(n, ast.Name) and n.id in functions and n.id not in selected)
    approved = json.loads((HERE/'approved.json').read_text())['files']
    code = '\n'.join(dict.fromkeys(declarations)) + '\nAPPROVED_FILES = ' + repr(approved) + '\n'
    code += '\n\n'.join(functions[name][1] for name in sorted(selected))
    return code


def document(entry, parameters=None, dynamic=False):
    code = host_source(entry)
    # Only the Restore document offers a choice, restricted to two fixed functions.
    if dynamic:
        code = host_source('rollback') + '\n' + '\n'.join(
            ast.get_source_segment((HERE/'host_operations.py').read_text(), n)
            for n in ast.parse((HERE/'host_operations.py').read_text()).body
            if isinstance(n, ast.FunctionDef) and n.name == 'verify_permission')
    invocation = "operation = os.environ.get('SSM_Operation', '')\n        require(operation in ('verify','restore'), 'unsupported operation or outdated SSM agent')\n        (verify_permission if operation == 'verify' else rollback)()" if dynamic else entry + '()'
    code += "\n\ntry:\n    os.umask(0o077)\n    require(os.geteuid()==0, 'SSM root required')\n    with open('/var/lock/f2r-pr914-release.lock','a') as lock:\n        fcntl.flock(lock, fcntl.LOCK_EX)\n        " + invocation + "\nexcept Exception as e:\n    print(json.dumps({'result':'FAIL','reason':str(e) if isinstance(e,RuntimeError) else type(e).__name__}))\n    sys.exit(1)\n"
    compile(code, '<ssm>', 'exec')
    return {'schemaVersion': '2.2', 'description': 'Fixed PR914 operation; no DB or original media writes',
            'parameters': parameters or {}, 'mainSteps': [{'action': 'aws:runShellScript', 'name': 'FixedOperation',
            'inputs': {'timeoutSeconds': '1200', 'runCommand': ['set -eu', "python3 - <<'F2R_914_PY'\n" + code + '\nF2R_914_PY']}}]}


def ref(name): return {'Ref': name}
def sub(value): return {'Fn::Sub': value}


def generate():
    docs = {
        'Backup': document('backup_or_verify'),
        'Deploy': document('deploy', {n: {'type':'String','allowedPattern':p,'interpolationType':'ENV_VAR'} for n,p in [('ReleaseSha','^[0-9a-f]{40}$'),('PatchSha256','^[0-9a-f]{64}$'),('ReleaseRunId','^[1-9][0-9]{0,19}$')]}),
        'Restore': document('rollback', {'Operation': {'type':'String','default':'verify','allowedValues':['verify','restore'],'interpolationType':'ENV_VAR'}, 'ReleaseRunId': {'type':'String','default':'0','allowedPattern':'^[0-9]{1,20}$','interpolationType':'ENV_VAR'}}, dynamic=True)
    }
    resources = {}
    for name, content in docs.items():
        resources[name+'Document'] = {'Type':'AWS::SSM::Document','DeletionPolicy':'Retain','UpdateReplacePolicy':'Retain',
            'Properties': {'Name':'F2R-914-Auto'+name,'DocumentType':'Command','DocumentFormat':'JSON','Content':content}}
    doc_arn = lambda n: sub('arn:${AWS::Partition}:ssm:${AWS::Region}:${AWS::AccountId}:document/'+n)
    for role, documents in [('Preflight',['F2R-914-MediaAudit','F2R-914-AutoBackup']), ('Deploy',['F2R-914-MediaAudit','F2R-914-AutoDeploy']), ('Restore',['F2R-914-MediaAudit','F2R-914-AutoRestore'])]:
        statements = [
            {'Effect':'Allow','Action':'ssm:SendCommand','Resource':[doc_arn(n) for n in documents]},
            {'Effect':'Allow','Action':'ssm:SendCommand','Resource':sub('arn:${AWS::Partition}:ec2:${AWS::Region}:${AWS::AccountId}:instance/${ProductionInstanceId}'),
             'Condition':{'StringEquals':{'ssm:resourceTag/Name':'f2r-app','ssm:resourceTag/Project':'f2r-migration'}}},
            {'Effect':'Allow','Action':'ssm:GetCommandInvocation','Resource':'*'}]
        if role == 'Deploy': statements.append({'Effect':'Allow','Action':'s3:PutObject','Resource':sub('arn:${AWS::Partition}:s3:::f2r-deploy-${AWS::AccountId}/pr914-approved/*')})
        resources[role+'Role'] = {'Type':'AWS::IAM::Role','Properties':{
            'RoleName':'github-actions-f2r-914-'+role.lower(),'MaxSessionDuration':3600,
            'AssumeRolePolicyDocument':{'Version':'2012-10-17','Statement':[{'Effect':'Allow','Principal':{'Federated':sub('arn:${AWS::Partition}:iam::${AWS::AccountId}:oidc-provider/token.actions.githubusercontent.com')},'Action':'sts:AssumeRoleWithWebIdentity',
            'Condition':{'StringEquals':{'token.actions.githubusercontent.com:aud':'sts.amazonaws.com','token.actions.githubusercontent.com:sub':ref('GitHubMainSubject')}}}]},
            'Policies':[{'PolicyName':'FixedPR914'+role,'PolicyDocument':{'Version':'2012-10-17','Statement':statements}}]}}
    resources['InstanceArtifactRead']={'Type':'AWS::IAM::Policy','Properties':{'PolicyName':'F2R914ApprovedPatchRead','Roles':[ref('ExistingInstanceRoleName')],
        'PolicyDocument':{'Version':'2012-10-17','Statement':[{'Effect':'Allow','Action':'s3:GetObject','Resource':sub('arn:${AWS::Partition}:s3:::f2r-deploy-${AWS::AccountId}/pr914-approved/*')}]}}}
    template = {'AWSTemplateFormatVersion':'2010-09-09','Description':'PR914 fixed release operations; preserves existing MediaAudit and existing deployment infrastructure',
        'Parameters': {'ProductionInstanceId':{'Type':'String','Default':INSTANCE,'AllowedValues':[INSTANCE]},
        'GitHubMainSubject':{'Type':'String','Default':SUBJECT,'AllowedValues':[SUBJECT,'repo:woori73/feel2real-buildit:ref:refs/heads/main']},
        'ExistingInstanceRoleName':{'Type':'String','AllowedPattern':'[A-Za-z0-9+=,.@_-]{1,64}'}},
        'Rules':{'CorrectTarget':{'Assertions':[{'Assert':{'Fn::Equals':[ref('AWS::AccountId'),ACCOUNT]},'AssertDescription':'Only approved production account'},
            {'Assert':{'Fn::Equals':[ref('AWS::Region'),REGION]},'AssertDescription':'Only Seoul region'}]}},
        'Resources':resources,'Outputs':{role+'RoleArn':{'Value':{'Fn::GetAtt':[role+'Role','Arn']}} for role in ['Preflight','Deploy','Restore']}}
    target=ROOT/'infra/pr914/stack.json'; target.write_text(json.dumps(template,ensure_ascii=False,separators=(',',':'))+'\n')
    for name,content in docs.items(): (ROOT/('infra/pr914/F2R-914-Auto'+name+'.json')).write_text(json.dumps(content,indent=2)+'\n')
    print('CloudFormation bytes:',target.stat().st_size)
    if target.stat().st_size > 51200: raise RuntimeError('template exceeds inline CloudFormation limit')

if __name__ == '__main__': generate()
