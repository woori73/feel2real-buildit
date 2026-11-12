"""Consume verified Studio observations; preserve failures for operator review."""
import datetime as dt
import json
import os
from pathlib import Path
import stat
import newstart_roster_sync as roster

INBOX=Path('/home/ec2-user/f2r-cafe-browser/roster-outbox')
ARCHIVE=Path('/var/lib/f2r-ebook/roster-synced')
STATUS=Path('/var/lib/f2r-ebook/roster-sync-status.json')


def consume(inbox=INBOX, archive=ARCHIVE, status=STATUS, execute=roster.sync):
    os.umask(0o077)
    archive.mkdir(mode=0o700,parents=True,exist_ok=True)
    totals={'synced':0,'review':0,'checked_at':dt.datetime.now(dt.timezone.utc).isoformat()}
    for file in sorted(inbox.glob('*.json')):
        try:
            fd=os.open(file,os.O_RDONLY|os.O_NOFOLLOW)
            with os.fdopen(fd) as stream:
                info=os.fstat(stream.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_size>16384:
                    raise ValueError('INVALID_OBSERVATION_FILE')
                item=json.load(stream)
            roster_id=execute(item)
            # Archive contains the verified snapshot, never account/session credentials.
            receipt={**item,'roster_id':roster_id,'synced_at':totals['checked_at']}
            target=archive/(item['member_tag']+'.json')
            temp=target.with_suffix('.tmp')
            temp.write_text(json.dumps(receipt,ensure_ascii=False))
            temp.chmod(0o600)
            temp.replace(target)
            file.unlink()
            totals['synced']+=1
        except Exception:
            # No automatic replacement on identity collisions or DB failures.
            totals['review']+=1
    temporary=status.with_suffix('.tmp')
    temporary.write_text(json.dumps(totals))
    temporary.chmod(0o600)
    temporary.replace(status)
    return totals


if __name__=='__main__':
    print(json.dumps(consume()))
