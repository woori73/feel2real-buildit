(async function () {
  'use strict';
  var status=document.getElementById('academycraft-status');
  var container=document.getElementById('academycraft-metrics');
  try {
    var context=await fetch('/academycraft/api/rea/integration/context',{credentials:'same-origin',cache:'no-store'});
    if(!context.ok) throw new Error(context.status===403?'연결된 학원 또는 조회 권한이 없습니다.':'현재 학원 현황을 불러올 수 없습니다.');
    var auth=await context.json();
    if(auth.role==='PARENT') throw new Error('학생 계정에는 학원 전체 현황을 표시하지 않습니다.');
    var res=await fetch('/academycraft/api/rea/'+encodeURIComponent(auth.academyId)+'/dashboard/stats',{credentials:'same-origin',cache:'no-store'});
    if(!res.ok) throw new Error('학원 현황 조회 권한 또는 서비스 상태를 확인하세요.');
    var data=await res.json(), today=data.todayAttendance;
    if(!Number.isSafeInteger(data.studentCount)||!Number.isSafeInteger(data.classCount)||!today) throw new Error('학원 현황 응답을 확인하세요.');
    var rows=[['학생 수',data.studentCount,'MBR-01-student-list.html'],['운영 중인 반',data.classCount,'CLS-01-class-list.html'],['오늘 등원 기록',today.checkInCount,'ATT-03-attendance-roster.html'],['결석 기록',today.absentCount,'ATT-03-attendance-roster.html']];
    var classesResponse=await fetch('/academycraft/api/rea/'+auth.academyId+'/classes',{credentials:'same-origin',cache:'no-store'});
    if(classesResponse.ok) {
      var classes=await classesResponse.json(), missing=new Set(), recorded=new Set(), complete=true;
      for(var clazz of classes) {
        var rosterResponse=await fetch('/academycraft/api/rea/'+auth.academyId+'/attendance/roster?classId='+encodeURIComponent(clazz.id)+'&date='+encodeURIComponent(today.date),{credentials:'same-origin',cache:'no-store'});
        if(!rosterResponse.ok) {complete=false;break;}
        var roster=await rosterResponse.json();
        for(var entry of roster.entries) (entry.status==='NONE'?missing:recorded).add(entry.studentId);
      }
      if(complete) {for(var id of recorded) missing.delete(id); rows.push(['오늘 반별 미기록 (중복 제외)',missing.size,'ATT-03-attendance-roster.html']);}
    }
    rows.forEach(function(row){ if(!Number.isSafeInteger(row[1])) return; var a=document.createElement('a'); a.href='/academycraft/screens/'+row[2]; a.textContent=row[0]+': '+row[1]; a.style.minHeight='44px'; container.appendChild(a); });
    status.textContent='집계일: '+today.date+' · 미기록은 기존 반별 출결부의 NONE 상태 기준입니다.';
  } catch(error) { status.textContent=error.message; }
})();
