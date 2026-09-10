import { ChangeEvent, useEffect, useState } from 'react'
import { api } from '../lib/api'

type ExamContext = { school:string; grade:string; year:number; semester:string; exam:string }
type SetType = '40'|'70'|'40A'|'40B'
type WeeklyStatus = 'REVIEW'|'CHANGES_REQUESTED'|'REJECTED'|'APPROVED'|'FINAL'|'PUBLISHED'
type WeeklyExam = {
  id:number; school:string; grade:string; year:number; semester:string; exam:string
  round:number; set_type:SetType; question_count:number; status:WeeklyStatus; version:number
  created_by:string; created_at:string; context_hash:string
}
type Props = {
  context:ExamContext; pastExamCount:number; schoolPrintCount:number; wrongCount:number
  aiWorkLogCount:number; hasDigitalTwin:boolean; lastContextUpdate?:string
  onOpenContext:()=>void; onOpenWeeklyWorkspace:()=>void; onOpenWrongReview:()=>void
}

const SET_POLICY:Record<string,SetType[]>={
  '압구정중학교|2학년':['40','70'],'압구정중|2학년':['40','70'],
  '신구중학교|2학년':['40','70'],'신구중|2학년':['40','70'],
  '신구중학교|3학년':['40A','40B'],'신구중|3학년':['40A','40B'],
  '신사중학교|3학년':['40A','40B'],'신사중|3학년':['40A','40B'],
}
const setTypesFor=(context:ExamContext)=>SET_POLICY[`${context.school}|${context.grade}`]||['40']
const query=(context:ExamContext)=>new URLSearchParams({...context,year:String(context.year)}).toString()
const readFile=(event:ChangeEvent<HTMLInputElement>,setter:(value:string)=>void)=>{
  const file=event.target.files?.[0];if(!file)return
  const reader=new FileReader();reader.onload=()=>setter(String(reader.result||''));reader.readAsText(file)
}
const statusLabel=(status:WeeklyStatus)=>({
  REVIEW:'검토 대기',CHANGES_REQUESTED:'수정 요청',REJECTED:'반려',
  APPROVED:'내용 승인',FINAL:'최종 확정',PUBLISHED:'학생 공개',
}[status])

export default function WeeklyExamHub(props:Props){
  const {context,pastExamCount,schoolPrintCount,wrongCount,aiWorkLogCount,hasDigitalTwin,lastContextUpdate,onOpenContext,onOpenWrongReview}=props
  const setTypes=setTypesFor(context),isPartial=context.school.includes('압구정')&&context.grade.includes('2')
  const [items,setItems]=useState<WeeklyExam[]>([]),[isAdmin,setIsAdmin]=useState(false),[notice,setNotice]=useState('')
  const [showRegister,setShowRegister]=useState(false),[round,setRound]=useState(1),[setType,setSetType]=useState<SetType>(setTypes[0])
  const [studentPaper,setStudentPaper]=useState(''),[answerExplanation,setAnswerExplanation]=useState(''),[verification,setVerification]=useState('')
  const load=async()=>{
    if(!context.school||!context.grade)return
    const [rows,me]=await Promise.all([
      api(`weekly-exams?${query(context)}`).then(r=>r.ok?r.json():[]),
      api('workspace/me').then(r=>r.ok?r.json():{}),
    ])
    setItems(Array.isArray(rows)?rows:[]);setIsAdmin(Boolean(me.workspace_admin))
  }
  useEffect(()=>{setSetType(setTypesFor(context)[0]);void load()},[context.school,context.grade,context.year,context.semester,context.exam])
  const open=(path:string)=>window.open(`/problem-generator/wiki/api/${path}`,'_blank','noopener')
  const register=async()=>{
    const response=await api('weekly-exams/review',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
      school:context.school,grade:context.grade,year:context.year,semester:context.semester,examType:context.exam,
      round,setType,questionCount:Number(setType.replace(/\D/g,'')),studentPaper,answerExplanation,
      productionVerification:verification,contextStatus:isPartial?'PARTIAL':'LIVING',
    })})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setNotice(body.detail||'REVIEW 등록에 실패했습니다.');return}
    setStudentPaper('');setAnswerExplanation('');setVerification('');setShowRegister(false)
    setNotice('세 산출물을 REVIEW 상태로 등록했습니다. 사용자 승인 전에는 공개되지 않습니다.');await load()
  }
  const transition=async(id:number,action:'approve'|'request_changes'|'reject'|'finalize'|'publish')=>{
    const response=await api(`weekly-exams/${id}/transition`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action})})
    const body=await response.json().catch(()=>({}))
    setNotice(response.ok?'상태를 변경했습니다.':body.detail||'상태를 변경하지 못했습니다.');if(response.ok)await load()
  }
  const latestFor=(type:SetType)=>items.filter(item=>item.set_type===type).sort((a,b)=>b.round-a.round||b.version-a.version)[0]

  return <section className="naesin-repeat-hub" aria-label="반복 제작 허브">
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
    <div className="current-context-card">
      <div><p className="kicker">CURRENT EXAM CONTEXT</p><h2>{context.school} · {context.grade}</h2><p>{context.year}년 · {context.semester} · {context.exam}</p></div>
      <div className="context-health">
        <span className={isPartial?'context-status partial':'context-status living'}>Context {isPartial?'PARTIAL':'LIVING'}</span>
        <small>기출 {pastExamCount} · 학습지/필기 {schoolPrintCount} · Digital Twin {hasDigitalTwin?'연결':'미연결'}</small>
        <small>제작·검수 기록 {aiWorkLogCount} · 학생 오답 {wrongCount}</small>
        {lastContextUpdate&&<small>마지막 Context 업데이트 {new Date(lastContextUpdate).toLocaleString('ko-KR')}</small>}
      </div>
      <button type="button" className="secondary-button" onClick={onOpenContext}>자료 보기 · AI Context</button>
    </div>

    <div className="repeat-entry-grid">
      <article className="repeat-entry-card weekly">
        <p className="kicker">WEEKLY EXAM</p><h2>주간 실전 모의고사</h2>
        <p>문제지·정답해설·출제근거를 한 세트로 REVIEW 등록하고 사용자 승인 뒤에만 공개합니다.</p>
        <div className="weekly-set-list" aria-label="주간 실전 모의고사 상태">
          {setTypes.map(type=>{const item=latestFor(type);return <span key={type} className={item?`weekly-status ${item.status.toLowerCase()}`:''}>
            <b>{type}</b><small>{item?`${item.round}회 · ${item.status} · ${statusLabel(item.status)}`:'준비 전'}</small>
          </span>})}
        </div>
        {items.length>0&&<div className="weekly-rounds">{items.map(item=><article key={item.id} className="weekly-exam-row">
          <div><b>{item.round}회 · {item.set_type}</b><span className={`status-badge ${item.status.toLowerCase()}`}>{item.status}</span>
            <small>{item.question_count}문항 · v{item.version} · {item.created_by||'미상'} · {new Date(item.created_at).toLocaleDateString('ko-KR')}</small></div>
          <div className="artifact-actions">
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/student-paper`)}>문제지 보기</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/answer-explanation`)}>정답해설 보기</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/production-verification`)}>출제근거 보기</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/context-snapshot`)}>제작 당시 Context</button>
          </div>
          {isAdmin&&<div className="approval-actions">
            {item.status==='REVIEW'&&<><button onClick={()=>void transition(item.id,'approve')}>승인</button><button onClick={()=>void transition(item.id,'request_changes')}>수정 요청</button><button onClick={()=>void transition(item.id,'reject')}>반려</button></>}
            {item.status==='APPROVED'&&<button onClick={()=>void transition(item.id,'finalize')}>FINAL 확정</button>}
            {item.status==='FINAL'&&<button onClick={()=>void transition(item.id,'publish')}>학생 공개</button>}
          </div>}
        </article>)}</div>}
        <button type="button" className="primary-action" onClick={()=>setShowRegister(value=>!value)}>제작물 3종 REVIEW 등록</button>
      </article>

      <article className="repeat-entry-card wrong-review">
        <p className="kicker">STUDENT ERROR CONTEXT</p><h2>오답 복습</h2>
        <p>학교 공통 Context와 섞지 않고 학생별 100발100중 오답을 누적합니다.</p>
        <div className="wrong-summary"><strong>{wrongCount}</strong><span>현재 시험 Context 오답 자료</span></div>
        <button type="button" className="primary-action" onClick={onOpenWrongReview}>오답 복습 열기</button>
      </article>
    </div>

    {showRegister&&<section className="work-panel weekly-register" aria-label="Weekly Exam REVIEW 등록">
      <div className="panel-heading"><div><p className="kicker">ONE SET · THREE ARTIFACTS</p><h2>주간 실전 모의고사 등록</h2><p>{context.school} · {context.grade} · {context.year}년 {context.semester} {context.exam}</p></div><button onClick={()=>setShowRegister(false)}>닫기</button></div>
      <div className="weekly-register-meta"><label>회차<input type="number" min="1" value={round} onChange={e=>setRound(Number(e.target.value))}/></label><label>세트<select value={setType} onChange={e=>setSetType(e.target.value as SetType)}>{setTypes.map(type=><option key={type}>{type}</option>)}</select></label></div>
      <label>학생용 문제지 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setStudentPaper)}/></label>
      <label>정답·해설 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setAnswerExplanation)}/></label>
      <label>제작·검수 MD<input type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" onChange={e=>readFile(e,setVerification)}/></label>
      <p className="field-hint">세 파일은 하나의 세트로 저장됩니다. 서버가 현재 Living Context Snapshot을 만들고 상태를 REVIEW로 고정합니다.</p>
      <button className="primary-action" disabled={!studentPaper||!answerExplanation||!verification} onClick={()=>void register()}>REVIEW 등록</button>
    </section>}
    <p className="approval-gate-note">DRAFT 준비 전 · AI 제작 완료는 공개가 아닙니다. REVIEW → 사용자 승인 → APPROVED → FINAL/PUBLISHED 순서를 서버가 검증합니다.</p>
  </section>
}
