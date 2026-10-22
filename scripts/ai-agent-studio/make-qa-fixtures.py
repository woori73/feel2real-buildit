#!/usr/bin/env python3
"""Synthetic byte-preservation fixtures only; never actual AI AGENT assets."""
import json,struct,subprocess,zipfile,zlib
from pathlib import Path
p=Path('/tmp/f2r-qa-assets');p.mkdir(exist_ok=True)
p.joinpath('qa-synthetic.md').write_text('# QA SYNTHETIC ONLY\n실제 작품 자료가 아닙니다. 원본 불변 검증.',encoding='utf8')
p.joinpath('qa-synthetic.html').write_text('<!doctype html><html><body>QA SYNTHETIC ONLY<script>window.f2rUnsafeExecuted=true</script></body></html>',encoding='utf8')
p.joinpath('qa-synthetic.json').write_text(json.dumps({'synthetic':True,'purpose':'upload preservation test'}))
with zipfile.ZipFile(p/'qa-synthetic.zip','w') as z:z.writestr('../never-extracted.txt','SYNTHETIC TEST ONLY')
def chunk(k,b):return struct.pack('>I',len(b))+k+b+struct.pack('>I',zlib.crc32(k+b)&0xffffffff)
p.joinpath('qa-synthetic.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([33,75,105])*16)*16))+chunk(b'IEND',b''))
objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>']
stream=b'BT /F1 15 Tf 20 100 Td (QA SYNTHETIC ONLY - not real episode data) Tj ET';objects.append(b'<< /Length '+str(len(stream)).encode()+b' >>\nstream\n'+stream+b'\nendstream')
body=b'%PDF-1.4\n';offsets=[0]
for i,obj in enumerate(objects,1):offsets.append(len(body));body+=f'{i} 0 obj\n'.encode()+obj+b'\nendobj\n'
xref=len(body);body+=f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode()+b''.join(f'{n:010d} 00000 n \n'.encode() for n in offsets[1:]);body+=f'trailer << /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF'.encode();p.joinpath('qa-synthetic.pdf').write_bytes(body)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=steelblue:s=160x90:d=1','-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart','-y',str(p/'qa-synthetic.mp4')],check=True)
print('Created seven SYNTHETIC test fixtures under /tmp/f2r-qa-assets only.')
