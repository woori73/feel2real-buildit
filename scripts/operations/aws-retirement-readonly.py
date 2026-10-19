import concurrent.futures,subprocess,json,hashlib,datetime,os
EXPECTED=os.environ['EXPECTED_ACCOUNT']
def aws(service,operation,region='ap-northeast-2',args=()):
    p=subprocess.run(['aws',service,operation,'--region',region,'--output','json',*args],capture_output=True,text=True,timeout=90)
    if p.returncode:
        import re
        error=re.search(r'\(([^)]+)\)',p.stderr)
        return {'BLOCKED':error.group(1) if error else 'request-failed','operation':service+':'+operation}
    try:return json.loads(p.stdout)
    except ValueError:return {'BLOCKED':'invalid-json','operation':service+':'+operation}
def emit(label,value):print(json.dumps({'section':label,'account':EXPECTED,'value':value},ensure_ascii=False),flush=True)
caller=aws('sts','get-caller-identity')
if caller.get('Account')!=EXPECTED:raise SystemExit('WRONG ACCOUNT; NO FURTHER ACTION')
emit('caller',{'account':caller['Account'],'role':caller['Arn'].split('assumed-role/')[-1].split('/')[0]})
regions=aws('ec2','describe-regions')
if 'Regions' not in regions:
    emit('regions',regions); regions=['ap-northeast-2']; emit('regionCoverage','LIMITED: Seoul only; all-region inventory unavailable')
else: regions=[r['RegionName'] for r in regions['Regions']]
# Identifiers/capacities only; no credentials, object keys, user records, tags or actual billing are emitted.
ops=[
('ec2','ec2','describe-instances',['--query','Reservations[].Instances[].{id:InstanceId,type:InstanceType,state:State.Name,volumes:BlockDeviceMappings[].Ebs.VolumeId,publicIp:PublicIpAddress,vpc:VpcId}']),
('ebs','ec2','describe-volumes',['--query','Volumes[].{id:VolumeId,type:VolumeType,sizeGiB:Size,state:State,encrypted:Encrypted,attachments:Attachments[].{instance:InstanceId,deleteOnTermination:DeleteOnTermination}}']),
('ebsSnapshots','ec2','describe-snapshots',['--owner-ids','self','--query','Snapshots[].{id:SnapshotId,volume:VolumeId,sizeGiB:VolumeSize,state:State,start:StartTime,encrypted:Encrypted,key:KmsKeyId}']),
('elasticIps','ec2','describe-addresses',['--query','Addresses[].{id:AllocationId,association:AssociationId,instance:InstanceId,ip:PublicIp,eni:NetworkInterfaceId}']),
('networkInterfaces','ec2','describe-network-interfaces',['--query','NetworkInterfaces[].{id:NetworkInterfaceId,type:InterfaceType,state:Status,instance:Attachment.InstanceId,publicIp:Association.PublicIp}']),
('natGateways','ec2','describe-nat-gateways',['--query','NatGateways[].{id:NatGatewayId,state:State,vpc:VpcId,subnet:SubnetId,ips:NatGatewayAddresses[].AllocationId}']),
('albs','elbv2','describe-load-balancers',['--query','LoadBalancers[].{arn:LoadBalancerArn,name:LoadBalancerName,type:Type,dns:DNSName,state:State.Code,vpc:VpcId,subnets:AvailabilityZones[].SubnetId}']),
('classicElbs','elb','describe-load-balancers',['--query','LoadBalancerDescriptions[].{name:LoadBalancerName,dns:DNSName,instances:Instances[].InstanceId}']),
('rds','rds','describe-db-instances',['--query','DBInstances[].{arn:DBInstanceArn,id:DBInstanceIdentifier,type:DBInstanceClass,state:DBInstanceStatus,engine:Engine,version:EngineVersion,sizeGiB:AllocatedStorage,storage:StorageType,multiAz:MultiAZ,backupDays:BackupRetentionPeriod,latestRestore:LatestRestorableTime,endpoint:Endpoint.Address,key:KmsKeyId,deletionProtection:DeletionProtection}']),
('rdsClusters','rds','describe-db-clusters',['--query','DBClusters[].{id:DBClusterIdentifier,arn:DBClusterArn,state:Status,engine:Engine,members:DBClusterMembers[].DBInstanceIdentifier,key:KmsKeyId}']),
('rdsSnapshots','rds','describe-db-snapshots',['--query','DBSnapshots[].{id:DBSnapshotIdentifier,arn:DBSnapshotArn,source:DBInstanceIdentifier,state:Status,type:SnapshotType,time:SnapshotCreateTime,sizeGiB:AllocatedStorage,encrypted:Encrypted,key:KmsKeyId}']),
('efs','efs','describe-file-systems',['--query','FileSystems[].{id:FileSystemId,state:LifeCycleState,size:SizeInBytes.Value}']),
('elasticache','elasticache','describe-cache-clusters',['--query','CacheClusters[].{id:CacheClusterId,type:CacheNodeType,state:CacheClusterStatus,nodes:NumCacheNodes}']),
('logStorage','logs','describe-log-groups',['--query','logGroups[].{name:logGroupName,storedBytes:storedBytes,retentionDays:retentionInDays}']),
('vpcEndpoints','ec2','describe-vpc-endpoints',['--query','VpcEndpoints[].{id:VpcEndpointId,type:VpcEndpointType,service:ServiceName,state:State}'])]
def inspect(region):
    out={}
    for label,service,operation,args in ops:
        try:value=aws(service,operation,region,args)
        except subprocess.TimeoutExpired:value={'BLOCKED':'timeout','operation':service+':'+operation}
        if label=='rds' and isinstance(value,list):
            for db in value:
                host=db.pop('endpoint',None)
                db['endpointSha256']=hashlib.sha256(host.encode()).hexdigest() if host else None
        if value:out[label]=value
    emit('region:'+region,out)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:list(pool.map(inspect,regions))
emit('scannedRegions',regions)
buckets=aws('s3api','list-buckets',args=['--query','Buckets[].Name']);emit('s3Buckets',buckets)
zones=aws('route53','list-hosted-zones',args=['--query','HostedZones[].{id:Id,name:Name,private:Config.PrivateZone}']);emit('dnsZones',zones)
if isinstance(zones,list):
    for zone in zones:
        if zone['name']=='feel2real.co.kr.':
            records=aws('route53','list-resource-record-sets',args=['--hosted-zone-id',zone['id'],'--query',"ResourceRecordSets[?Type=='A' || Type=='AAAA' || Type=='CNAME' || Type=='NS'].{name:Name,type:Type,alias:AliasTarget.DNSName,values:ResourceRecords[].Value}"])
            emit('feel2realDNS',records)
emit('registeredDomains',aws('route53domains','list-domains','us-east-1',args=['--query','Domains[].{name:DomainName,autoRenew:AutoRenew,expiry:Expiry}']))
emit('cloudfront',aws('cloudfront','list-distributions',args=['--query','DistributionList.Items[].{id:Id,enabled:Enabled,aliases:Aliases.Items,origins:Origins.Items[].DomainName}']))
if EXPECTED=='425805163960':
    for bucket in ['f2r-deploy-425805163960','f2r-uploads-425805163960']:
        emit('s3Encryption:'+bucket,aws('s3api','get-bucket-encryption',args=['--bucket',bucket]))
    emit('latestAcademyBackup',aws('s3api','list-objects-v2',args=['--bucket','f2r-deploy-425805163960','--prefix','backups/academycraft/','--query','sort_by(Contents,&LastModified)[-1].{created:LastModified,bytes:Size}']))
    hostcmd='''set +x; set -a; . /etc/f2r/env; set +a
python3 - <<'HOSTPY'
import os,subprocess,hashlib,json,urllib.request,zipfile
out={'dbEndpointSha256':hashlib.sha256(os.getenv('DB_HOST','').encode()).hexdigest(),'s3UsesNewAccount':'425805163960' in os.getenv('S3_BUCKET','')}
for service in ['f2r-app','buildit-wiki','academycraft']:
 p=subprocess.run(['systemctl','is-active',service],capture_output=True,text=True);out[service]=p.stdout.strip()
for port,path in [(8080,'/actuator/health'),(8000,'/api/health'),(8082,'/actuator/health')]:
 try:
  with urllib.request.urlopen('http://127.0.0.1:'+str(port)+path,timeout=8) as r:out['health'+str(port)]=r.status
 except Exception:out['health'+str(port)]='unavailable'
sql="START TRANSACTION READ ONLY; SELECT 'admin_existing',COUNT(*) FROM users WHERE user_id=4 AND login_id='admin-f2r' AND role='ADMIN'; SELECT 'modu_existing',COUNT(*) FROM users WHERE user_id=54 AND login_id='modu' AND role='TUTOR'; SELECT 'academy12_existing',COUNT(*) FROM academies WHERE academy_id=12 AND tutor_user_id=54; SELECT 'tables',COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE(); SELECT 'flyway_failures',COUNT(*) FROM flyway_schema_history WHERE success=0; ROLLBACK;"
env=os.environ.copy();env['MYSQL_PWD']=os.getenv('DB_PASSWORD','')
p=subprocess.run(['mysql','--batch','--skip-column-names','--connect-timeout=8','-h',os.getenv('DB_HOST',''),'-P',os.getenv('DB_PORT','3306'),'-u',os.getenv('DB_USER',''),os.getenv('DB_NAME',''),'-e',sql],env=env,capture_output=True,text=True)
out['readonlyDB']=p.stdout.strip() if p.returncode==0 else 'query-blocked'
out['academyEnvProtected']=os.path.isfile('/etc/academycraft/env') and os.stat('/etc/academycraft/env').st_mode & 0o077 == 0
print(json.dumps(out))
HOSTPY'''
    sent=aws('ssm','send-command',args=['--instance-ids','i-0414de33d2803c723','--document-name','AWS-RunShellScript','--parameters',json.dumps({'commands':[hostcmd]})])
    cid=sent.get('Command',{}).get('CommandId')
    if cid:
        import time
        for _ in range(30):
            inv=aws('ssm','get-command-invocation',args=['--command-id',cid,'--instance-id','i-0414de33d2803c723'])
            if inv.get('Status') not in ('Pending','InProgress','Delayed'):break
            time.sleep(2)
        emit('newHostReadOnly',{'status':inv.get('Status'),'output':inv.get('StandardOutputContent','') if inv.get('Status')=='Success' else 'blocked; error details suppressed'})
    else:emit('newHostReadOnly',sent)
for host in ['feel2real.co.kr','academy.feel2real.co.kr','f2r-alb-2551937.ap-northeast-2.elb.amazonaws.com']:
    p=subprocess.run(['getent','ahostsv4',host],capture_output=True,text=True)
    emit('publicDNS:'+host,sorted({l.split()[0] for l in p.stdout.splitlines() if l}))
for host,path in [('feel2real.co.kr','/'),('feel2real.co.kr','/grammar-items'),('feel2real.co.kr','/ledger'),('feel2real.co.kr','/dashboard'),('feel2real.co.kr','/testprep'),('academy.feel2real.co.kr','/actuator/health')]:
    p=subprocess.run(['curl','--silent','--show-error','--max-time','20','--output','/dev/null','--write-out','%{http_code} %{ssl_verify_result}','https://'+host+path],capture_output=True,text=True)
    emit('anonymousHTTPS:'+host+path,{'result':p.stdout.strip(),'exit':p.returncode})
# Actual billing is deliberately private-only: NEVER enabled in a public GitHub workflow.
if os.environ.get('PRIVATE_BILLING')=='1':
    today=datetime.date.today(); start=today.replace(day=1)
    emit('PRIVATE_MONTH_TO_DATE_COST',aws('ce','get-cost-and-usage','us-east-1',args=['--time-period','Start='+start.isoformat()+',End='+today.isoformat(),'--granularity','MONTHLY','--metrics','UnblendedCost','--group-by','Type=DIMENSION,Key=SERVICE','Type=DIMENSION,Key=REGION']))
emit('safety','READ ONLY; no stop/start/restart/create/modify/delete; billing and secrets not printed')
