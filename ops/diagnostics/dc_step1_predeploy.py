"""템플릿 디렉터리 속성(lsattr) 읽기 전용 확인. 변경 없음."""
import json, subprocess


def sh(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=40)
    return (p.stdout or p.stderr or '').strip()


T = '/opt/designcraft/templates'
out = {'lsattr_lines_with_i_flag': sh(f"lsattr -R {T} 2>&1 | awk '$1 ~ /i/' | head -20"),
       'lsattr_all_files_sample': sh(f"lsattr -R {T} 2>&1 | head -12"),
       'lsattr_version': sh('lsattr -V 2>&1 | head -1'),
       'distinct_flag_strings': sh(f"lsattr -R {T} 2>&1 | awk 'NF==2 {{print $1}}' | sort | uniq -c"),
       'fs_type': sh(f"findmnt -n -o FSTYPE,OPTIONS --target {T}"),
       'ls_l_one_font': sh(f"ls -l '{T}/ko-textbook-a4/Document Fonts/' "),
       'getfacl_one_font': sh(f"getfacl -p '{T}/ko-textbook-a4/Document Fonts/NotoSansKR-Regular.ttf' 2>&1 | head -8"),
       'xattrs_one_font': sh(f"command -v getfattr >/dev/null && getfattr -d -m - '{T}/ko-textbook-a4/Document Fonts/NotoSansKR-Regular.ttf' 2>&1 | head -5 || echo no-getfattr"),
       'selinux': sh('getenforce 2>&1'),
       'ec2user_group_info': sh('id ec2-user')}
print(json.dumps(out, ensure_ascii=False, indent=1))
