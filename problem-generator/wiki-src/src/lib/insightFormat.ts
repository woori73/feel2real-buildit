export type InsightLike={source?:string;created_at:string;school?:string;grade?:string;year?:number|string|null;semester?:string;exam?:string;student_context?:string;lesson_context?:string}

const DATE_OPTIONS:Intl.DateTimeFormatOptions={year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'2-digit'}

/** 예: "2026. 10. 16. 오전 7:25" (초 제외). 해석 불가한 값은 빈 문자열. */
export const formatInsightDate=(value:string):string=>{const d=new Date(value);return Number.isNaN(d.getTime())?'':d.toLocaleString('ko-KR',DATE_OPTIONS)}

/** "신사중 · 3학년 · 2026년 2학기 · 기말고사" — 값이 없는 항목은 출력하지 않는다. */
export const insightExamLine=(i:InsightLike):string=>[i.school,i.grade,i.year?`${i.year}년`:null,i.semester,i.exam].filter(Boolean).join(' · ')

export const insightStudentLine=(i:InsightLike):string=>[i.student_context&&`학생 · ${i.student_context}`,i.lesson_context&&`수업 · ${i.lesson_context}`].filter(Boolean).join(' · ')
