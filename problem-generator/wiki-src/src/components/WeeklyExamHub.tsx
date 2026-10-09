import { ChangeEvent, useEffect, useRef, useState } from 'react'
import { api } from '../lib/api'

type ExamContext = { school:string; grade:string; year:number; semester:string; exam:string }
// 1단계 구조조정: 40/70/40A/40B 구분 제거. 신규는 'STD' 단일 유형.
// 기존 DB 값은 표시용으로 유지.
type SetType = 'STD'|'40'|'70'|'40A'|'40B'
type WeeklyStatus = 'DRAFT'|'FINAL'
type WeeklyExam = {
  id:number; school:string; grade:string; year:number; semester:string; exam:string
  round:number; set_type:SetType; question_count:number; status:WeeklyStatus; version:number
  created_by:string; created_at:string; context_hash:string
  student_paper_name?:string; answer_explanation_name?:string; verification_name?:string
  context_snapshot?:{contextStatus?:'PARTIAL'|'LIVING'|'COMPLETE'}
}
type Props = {
  context:ExamContext; pastExamCount:number; schoolPrintCount:number; wrongCount:number
  aiWorkLogCount:number; hasDigitalTwin:boolean; lastContextUpdate?:string
  onOpenContext:()=>void; onOpenWeeklyWorkspace:()=>void; onOpenWrongReview:()=>void
  studentTotalWrongCount?:number|null; onOpenStudentWrongAnswers?:()=>void
}

// 1단계 구조조정: 학교별 세트 정책 제거 — 문항 수 구분 없이 단일 유형
const _SET_POLICY_REMOVED:Record<string,SetType[]>={
  '압구정중학교|2학년':['40','70'],'압구정중|2학년':['40','70'],
  '신구중학교|2학년':['40','70'],'신구중|2학년':['40','70'],
  '신구중학교|3학년':['40A','40B'],'신구중|3학년':['40A','40B'],
  '신사중학교|3학년':['40A','40B'],'신사중|3학년':['40A','40B'],
}
const query=(context:ExamContext)=>new URLSearchParams({...context,year:String(context.year)}).toString()
const readFile=(event:ChangeEvent<HTMLInputElement>,setter:(value:string)=>void,setterName?:(value:string)=>void)=>{
  const file=event.target.files?.[0];if(!file)return
  if(setterName)setterName(file.name)
  const reader=new FileReader();reader.onload=()=>setter(String(reader.result||''));reader.readAsText(file)
}

// 회차별 자료 간소화: 3종 자료 × 보기/다운로드/교체
type MaterialKind='paper'|'expl'|'verify'
const MATERIALS:{kind:MaterialKind;label:string;apiKind:string;accept:string}[]=[
  {kind:'paper',label:'문제지',apiKind:'student-paper',accept:'.html,.htm,text/html'},
  {kind:'expl',label:'해설지',apiKind:'answer-explanation',accept:'.html,.htm,text/html'},
  {kind:'verify',label:'출제검수',apiKind:'production-verification',accept:'.md,.markdown,.txt,text/markdown,text/plain'},
]
const hasMaterial=(item:WeeklyExam,kind:MaterialKind):boolean=>{
  if(kind==='paper')return Boolean(item.student_paper_name)
  if(kind==='expl')return Boolean(item.answer_explanation_name)
  return Boolean(item.verification_name)
}

export default function WeeklyExamHub(props:Props){
  const {context,pastExamCount,schoolPrintCount,wrongCount,aiWorkLogCount,hasDigitalTwin,lastContextUpdate,onOpenContext,onOpenWrongReview}=props
  const [items,setItems]=useState<WeeklyExam[]>([]),[canManage,setCanManage]=useState(false),[notice,setNotice]=useState('')
  const [showRegister,setShowRegister]=useState(false),[round,setRound]=useState(1),[setType,setSetType]=useState<SetType>('STD'),[questionCount,setQuestionCount]=useState(40)
  // 자료별 교체: 교체 대상 회차+자료를 지정하고 숨은 파일 입력으로 단일 파일 교체
  const [replaceTarget,setReplaceTarget]=useState<{id:number;kind:MaterialKind}|null>(null)
  const [replacing,setReplacing]=useState(false)
  const replaceInputRef=useRef<HTMLInputElement>(null)
  const [contextStatus,setContextStatus]=useState<'PARTIAL'|'LIVING'|'COMPLETE'>('PARTIAL')
  const [studentPaper,setStudentPaper]=useState(''),[answerExplanation,setAnswerExplanation]=useState(''),[verification,setVerification]=useState('')
  const load=async()=>{
    if(!context.school||!context.grade)return
    const [rows,me]=await Promise.all([
      api(`weekly-exams?${query(context)}`).then(r=>r.ok?r.json():[]),
      api('workspace/me').then(r=>r.ok?r.json():{}),
    ])
    setItems(Array.isArray(rows)?rows:[]);setCanManage(Boolean((me as {user_id?:number|string}).user_id))
  }
  useEffect(()=>{setSetType('STD');void load()},[context.school,context.grade,context.year,context.semester,context.exam])
  const open=(path:string)=>window.open(`/problem-generator/wiki/api/${path}`,'_blank','noopener')
  const register=async()=>{
    const response=await api('weekly-exams/draft',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
      school:context.school,grade:context.grade,year:context.year,semester:context.semester,examType:context.exam,
      round,setType,questionCount:Number(questionCount)||0,studentPaper,answerExplanation,
      productionVerification:verification,contextStatus,
    })})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setNotice(body.detail||'DRAFT 등록에 실패했습니다.');return}
    setStudentPaper('');setAnswerExplanation('');setVerification('');setShowRegister(false)
    setNotice('세 산출물을 DRAFT로 등록했습니다. FINAL 확정 전까지 제작·수정 중입니다.');await load()
  }
  const startReplace=(id:number,kind:MaterialKind)=>{
    if(replacing)return
    const mat=MATERIALS.find(m=>m.kind===kind);if(!mat)return
    setReplaceTarget({id,kind})
    const input=replaceInputRef.current
    if(input){input.accept=mat.accept;input.value='';input.click()}
  }
  const handleReplaceFile=(event:ChangeEvent<HTMLInputElement>)=>{
    const file=event.target.files?.[0]
    const target=replaceTarget
    if(!file||!target){setReplaceTarget(null);return}
    setReplacing(true)
    const reader=new FileReader()
    reader.onload=()=>{
      const content=String(reader.result||'')
      const body:Record<string,string>={}
      if(target.kind==='paper'){body.studentPaper=content;body.studentPaperName=file.name}
      else if(target.kind==='expl'){body.answerExplanation=content;body.answerExplanationName=file.name}
      else{body.productionVerification=content;body.productionVerificationName=file.name}
      void (async()=>{
        try{
          const response=await api(`weekly-exams/${target.id}/artifacts`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
          const data=await response.json().catch(()=>({}))
          if(!response.ok){setNotice(`교체에 실패했습니다: ${data.detail||'자료 교체에 실패했습니다.'}`);return}
          const label=MATERIALS.find(m=>m.kind===target.kind)?.label||'자료'
          setNotice(`${label}를 교체했습니다.`);await load()
        }finally{
          setReplacing(false);setReplaceTarget(null)
        }
      })()
    }
    reader.onerror=()=>{setNotice('파일을 읽지 못했습니다.');setReplacing(false);setReplaceTarget(null)}
    reader.readAsText(file)
  }
  const latestFor=()=>items.slice().sort((a,b)=>b.round-a.round||b.version-a.version)[0]
  const latestFinal=items.filter(item=>item.status==='FINAL').sort((a,b)=>b.round-a.round||b.version-a.version)[0]
  const currentStatus=items.slice().sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at))[0]?.context_snapshot?.contextStatus||'PARTIAL'
  const nextRound=Math.max(0,...items.map(item=>item.round))+1

  return <section className="naesin-repeat-hub" aria-label="반복 제작 허브">
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
    <input ref={replaceInputRef} type="file" style={{display:'none'}} aria-hidden="true" tabIndex={-1} onChange={handleReplaceFile}/>
    <div className="current-context-card">
      <div><p className="kicker">CURRENT EXAM CONTEXT</p><h2>{context.school} · {context.grade}</h2><p>{context.year}년 · {context.semester} · {context.exam}</p></div>
      <div className="context-health">
        <span className={currentStatus==='COMPLETE'?'context-status living':'context-status partial'}>Context {currentStatus}</span>
        <small>기출 {pastExamCount} · 학습지/필기 {schoolPrintCount} · Digital Twin {hasDigitalTwin?'연결':'미연결'}</small>
        <small>제작·검수 기록 {aiWorkLogCount} · 학생 오답 {wrongCount}</small>
        {lastContextUpdate&&<small>마지막 Context 업데이트 {new Date(lastContextUpdate).toLocaleString('ko-KR')}</small>}
      </div>
      <button type="button" className="secondary-button" onClick={onOpenContext}>자료 보기 · AI Context</button>
    </div>

    <div className="repeat-entry-grid">
      <article className="repeat-entry-card weekly">
        <p className="kicker">WEEKLY EXAM</p><h2>주간 실전 모의고사</h2>
        <p>문제지·해설지·출제검수를 회차별로 보관합니다.</p>
        <div className="next-round-summary"><b>다음 회차 준비 · {nextRound}회</b>
          <span>현재 Context 전체를 다시 계산해 Blueprint를 준비합니다.</span>
          <small>최근 FINAL {latestFinal?latestFinal.round+'회':'없음'} · 학생 오답 {wrongCount}건</small>
          {latestFinal&&<button onClick={()=>open(`weekly-exams/${latestFinal.id}/artifacts/production-verification`)}>최근 검수 MD 참조</button>}
        </div>
        <div className="weekly-set-list" aria-label="주간 실전 모의고사 상태">
          {(()=>{const item=latestFor();return <span className={item?`weekly-status ${item.status.toLowerCase()}`:''}>
            <b>최근</b><small>{item?`${item.round}회 · ${item.question_count}문항 · ${item.status}`:'준비 전'}</small>
          </span>})()}
        </div>
        {items.length>0&&<div className="weekly-rounds">{items.map(item=><article key={item.id} className="weekly-exam-row simple">
          <div className="round-title"><b>{item.round}회</b></div>
          <div className="material-table" role="table" aria-label={`${item.round}회 자료`}>
            {MATERIALS.map(mat=>{
              const exists=hasMaterial(item,mat.kind)
              return <div key={mat.kind} className="material-row" role="row">
                <span className="material-label" role="cell">{mat.label}</span>
                <div className="material-actions" role="cell">
                  {exists?<>
                    <button type="button" onClick={()=>open(`weekly-exams/${item.id}/artifacts/${mat.apiKind}`)}>보기</button>
                    <button type="button" onClick={()=>open(`weekly-exams/${item.id}/artifacts/${mat.apiKind}/download`)}>다운로드</button>
                    {canManage&&<button type="button" disabled={replacing} onClick={()=>startReplace(item.id,mat.kind)}>{replacing&&replaceTarget?.id===item.id&&replaceTarget?.kind===mat.kind?'교체 중…':'교체'}</button>}
                  </>:<>
                    {canManage
                      ?<button type="button" disabled={replacing} onClick={()=>startReplace(item.id,mat.kind)}>등록</button>
                      :<span className="material-missing">미등록</span>}
                  </>}
                </div>
              </div>
            })}
          </div>
        </article>)}</div>}
        <button type="button" className="primary-action" onClick={()=>setShowRegister(value=>!value)}>제작물 3종 DRAFT 등록</button>
      </article>

      <article className="repeat-entry-card wrong-review">
        <p className="kicker">STUDENT ERROR CONTEXT</p><h2>오답 복습</h2>
        <p>학교 공통 Context와 섞지 않고 학생별 100발100중 오답을 누적합니다.</p>
        <div className="wrong-summary"><strong>{wrongCount}</strong><span>현재 시험 Context 오답 자료</span></div>
        <button type="button" className="primary-action" onClick={onOpenWrongReview}>오답 복습 열기</button>
      </article>

    </div>

    {showRegister&&<section className="work-panel weekly-register" aria-label="Weekly Exam DRAFT 등록">
      <div className="panel-heading"><div><p className="kicker">ONE SET · THREE ARTIFACTS</p><h2>주간 실전 모의고사 등록</h2><p>{context.school} · {context.grade} · {context.year}년 {context.semester} {context.exam}</p></div><button onClick={()=>setShowRegister(false)}>닫기</button></div>
      <div className="weekly-register-meta"><label>회차<input type="number" min="1" value={round} onChange={e=>setRound(Number(e.target.value))}/></label><label>문항 수<input type="number" min="1" max="200" value={questionCount} onChange={e=>setQuestionCount(Number(e.target.value))}/></label><label>Context 상태<select value={contextStatus} onChange={e=>setContextStatus(e.target.value as 'PARTIAL'|'LIVING'|'COMPLETE')}><option>PARTIAL</option><option>LIVING</option><option>COMPLETE</option></select></label></div>
      <label>학생용 문제지 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setStudentPaper)}/></label>
      <label>정답·해설 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setAnswerExplanation)}/></label>
      <label>제작·검수 MD<input type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" onChange={e=>readFile(e,setVerification)}/></label>
      <p className="field-hint">세 파일은 하나의 세트로 저장됩니다. 서버가 현재 Living Context Snapshot을 보존하고 상태를 DRAFT로 고정합니다.</p>
      <button className="primary-action" disabled={!studentPaper||!answerExplanation||!verification} onClick={()=>void register()}>DRAFT 등록</button>
    </section>}
    <p className="approval-gate-note">원문은 운영자 전용입니다.</p>
  </section>
}
