import { ChangeEvent, useEffect, useState } from 'react'
import { api } from '../lib/api'

type ExamContext = { school:string; grade:string; year:number; semester:string; exam:string }
type SetType = '40'|'70'|'40A'|'40B'
type WeeklyStatus = 'DRAFT'|'FINAL'
type WeeklyExam = {
  id:number; school:string; grade:string; year:number; semester:string; exam:string
  round:number; set_type:SetType; question_count:number; status:WeeklyStatus; version:number
  created_by:string; created_at:string; context_hash:string
  context_snapshot?:{contextStatus?:'PARTIAL'|'LIVING'|'COMPLETE'}
}
type Props = {
  context:ExamContext; pastExamCount:number; schoolPrintCount:number; wrongCount:number
  aiWorkLogCount:number; hasDigitalTwin:boolean; lastContextUpdate?:string
  onOpenContext:()=>void; onOpenWeeklyWorkspace:()=>void; onOpenWrongReview:()=>void
  studentTotalWrongCount?:number|null; onOpenStudentWrongAnswers?:()=>void
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
const statusLabel=(status:WeeklyStatus)=>status==='FINAL'?'제작 완료':'제작·수정 중'

export default function WeeklyExamHub(props:Props){
  const {context,pastExamCount,schoolPrintCount,wrongCount,aiWorkLogCount,hasDigitalTwin,lastContextUpdate,onOpenContext,onOpenWrongReview,studentTotalWrongCount,onOpenStudentWrongAnswers}=props
  const setTypes=setTypesFor(context)
  const [items,setItems]=useState<WeeklyExam[]>([]),[canManage,setCanManage]=useState(false),[notice,setNotice]=useState('')
  const [showRegister,setShowRegister]=useState(false),[round,setRound]=useState(1),[setType,setSetType]=useState<SetType>(setTypes[0])
  const [replaceId,setReplaceId]=useState<number|null>(null),[confirmDeleteId,setConfirmDeleteId]=useState<number|null>(null)
  const [replacePaper,setReplacePaper]=useState(''),[replaceExpl,setReplaceExpl]=useState(''),[replaceMd,setReplaceMd]=useState('')
  const [contextStatus,setContextStatus]=useState<'PARTIAL'|'LIVING'|'COMPLETE'>('PARTIAL')
  const [studentPaper,setStudentPaper]=useState(''),[answerExplanation,setAnswerExplanation]=useState(''),[verification,setVerification]=useState('')
  const load=async()=>{
    if(!context.school||!context.grade)return
    const [rows,me]=await Promise.all([
      api(`weekly-exams?${query(context)}`).then(r=>r.ok?r.json():[]),
      api('workspace/me').then(r=>r.ok?r.json() as Promise<{user_id?:number|string}>:{}),
    ])
    setItems(Array.isArray(rows)?rows:[]);setCanManage(Boolean((me as {user_id?:number|string}).user_id))
  }
  useEffect(()=>{setSetType(setTypesFor(context)[0]);void load()},[context.school,context.grade,context.year,context.semester,context.exam])
  const open=(path:string)=>window.open(`/problem-generator/wiki/api/${path}`,'_blank','noopener')
  const register=async()=>{
    const response=await api('weekly-exams/draft',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
      school:context.school,grade:context.grade,year:context.year,semester:context.semester,examType:context.exam,
      round,setType,questionCount:Number(setType.replace(/\D/g,'')),studentPaper,answerExplanation,
      productionVerification:verification,contextStatus,
    })})
    const body=await response.json().catch(()=>({}))
    if(!response.ok){setNotice(body.detail||'DRAFT 등록에 실패했습니다.');return}
    setStudentPaper('');setAnswerExplanation('');setVerification('');setShowRegister(false)
    setNotice('세 산출물을 DRAFT로 등록했습니다. FINAL 확정 전까지 제작·수정 중입니다.');await load()
  }
  const transition=async(id:number,action:'finalize'|'reopen')=>{
    const response=await api(`weekly-exams/${id}/transition`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action})})
    const body=await response.json().catch(()=>({}))
    setNotice(response.ok?'상태를 변경했습니다.':body.detail||'상태를 변경하지 못했습니다.');if(response.ok)await load()
  }
  const removeDraft=async(id:number)=>{
    const response=await api(`weekly-exams/${id}`,{method:'DELETE'})
    const body=await response.json().catch(()=>({}))
    setNotice(response.ok?'DRAFT 세트를 삭제했습니다.':body.detail||'삭제하지 못했습니다.')
    setConfirmDeleteId(null);if(response.ok)await load()
  }
  const replaceArtifact=async(id:number,kind:'paper'|'expl'|'md')=>{
    const body:Record<string,string>={}
    if(kind==='paper'&&replacePaper)body.studentPaper=replacePaper
    if(kind==='expl'&&replaceExpl)body.answerExplanation=replaceExpl
    if(kind==='md'&&replaceMd)body.productionVerification=replaceMd
    if(Object.keys(body).length===0)return
    const response=await api(`weekly-exams/${id}/artifacts`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
    const data=await response.json().catch(()=>({}))
    if(!response.ok){setNotice(data.detail||'자료 교체에 실패했습니다.');return}
    setReplacePaper('');setReplaceExpl('');setReplaceMd('');setReplaceId(null)
    setNotice('선택한 자료를 교체했습니다. 회차·세트·상태는 그대로 유지됩니다.');await load()
  }
  const latestFor=(type:SetType)=>items.filter(item=>item.set_type===type).sort((a,b)=>b.round-a.round||b.version-a.version)[0]
  const latestFinal=items.filter(item=>item.status==='FINAL').sort((a,b)=>b.round-a.round||b.version-a.version)[0]
  const currentStatus=items.slice().sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at))[0]?.context_snapshot?.contextStatus||'PARTIAL'
  const nextRound=Math.max(0,...items.map(item=>item.round))+1

  return <section className="naesin-repeat-hub" aria-label="반복 제작 허브">
    {notice&&<div className="tool-notice" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
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
        <p>문제지·정답해설·출제근거를 운영자 전용으로 보관하고 DRAFT에서 FINAL로 확정합니다.</p>
        <div className="next-round-summary"><b>다음 회차 준비 · {nextRound}회</b>
          <span>현재 Context 전체를 다시 계산해 {setTypes.join(' / ')} Blueprint를 준비합니다.</span>
          <small>최근 FINAL {latestFinal?latestFinal.round+'회 · '+latestFinal.set_type:'없음'} · 학생 오답 {wrongCount}건</small>
          {latestFinal&&<button onClick={()=>open(`weekly-exams/${latestFinal.id}/artifacts/production-verification`)}>최근 검수 MD 참조</button>}
        </div>
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
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/student-paper/download`)}>문제지 HTML 다운로드</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/answer-explanation`)}>정답해설 보기</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/answer-explanation/download`)}>정답해설 HTML 다운로드</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/artifacts/production-verification`)}>출제근거 보기</button>
            <button onClick={()=>open(`weekly-exams/${item.id}/context-snapshot`)}>제작 당시 Context</button>
          </div>
          {canManage&&<div className="approval-actions">
            {item.status==='DRAFT'
              ? <><button onClick={()=>void transition(item.id,'finalize')}>FINAL 확정</button>
                <button onClick={()=>{setReplaceId(replaceId===item.id?null:item.id);setReplacePaper('');setReplaceExpl('');setReplaceMd('')}}>자료 교체</button>
                {confirmDeleteId===item.id
                  ? <><span>정말 삭제할까요?</span><button onClick={()=>void removeDraft(item.id)}>삭제 확인</button><button onClick={()=>setConfirmDeleteId(null)}>취소</button></>
                  : <button onClick={()=>setConfirmDeleteId(item.id)}>삭제</button>}</>
              : <button onClick={()=>void transition(item.id,'reopen')}>DRAFT로 다시 열기</button>}
          </div>}
          {replaceId===item.id&&item.status==='DRAFT'&&<div className="artifact-actions replace-panel">
            <label>문제지 교체<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setReplacePaper)}/></label>
            <button className="primary-action" disabled={!replacePaper} onClick={()=>void replaceArtifact(item.id,'paper')}>문제지 교체 저장</button>
            <label>정답해설 교체<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setReplaceExpl)}/></label>
            <button className="primary-action" disabled={!replaceExpl} onClick={()=>void replaceArtifact(item.id,'expl')}>정답해설 교체 저장</button>
            <label>출제근거 교체<input type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" onChange={e=>readFile(e,setReplaceMd)}/></label>
            <button className="primary-action" disabled={!replaceMd} onClick={()=>void replaceArtifact(item.id,'md')}>출제근거 교체 저장</button>
            <button onClick={()=>setReplaceId(null)}>닫기</button>
          </div>}
        </article>)}</div>}
        <button type="button" className="primary-action" onClick={()=>setShowRegister(value=>!value)}>제작물 3종 DRAFT 등록</button>
      </article>

      <article className="repeat-entry-card wrong-review">
        <p className="kicker">STUDENT ERROR CONTEXT</p><h2>오답 복습</h2>
        <p>학교 공통 Context와 섞지 않고 학생별 100발100중 오답을 누적합니다.</p>
        {/* 학생 전체 누적 오답과 현재 시험 Context에 연결된 오답은 같은 숫자가 아니다. 범위를 명시한다. */}
        <div className="wrong-summary"><strong>{wrongCount}</strong><span>이 시험 범위와 연결된 오답</span></div>
        {studentTotalWrongCount !== null && studentTotalWrongCount !== undefined && wrongCount !== studentTotalWrongCount && <p className="field-hint">현재 Context와 무관한 학생 전체 누적 오답은 {studentTotalWrongCount}건입니다.</p>}
        {onOpenStudentWrongAnswers && <button type="button" className="secondary-button" onClick={onOpenStudentWrongAnswers}>학생 전체 오답 보기</button>}
        <button type="button" className="primary-action" onClick={onOpenWrongReview}>이 시험 범위 오답 보기</button>
      </article>
    </div>

    {showRegister&&<section className="work-panel weekly-register" aria-label="Weekly Exam DRAFT 등록">
      <div className="panel-heading"><div><p className="kicker">ONE SET · THREE ARTIFACTS</p><h2>주간 실전 모의고사 등록</h2><p>{context.school} · {context.grade} · {context.year}년 {context.semester} {context.exam}</p></div><button onClick={()=>setShowRegister(false)}>닫기</button></div>
      <div className="weekly-register-meta"><label>회차<input type="number" min="1" value={round} onChange={e=>setRound(Number(e.target.value))}/></label><label>세트<select value={setType} onChange={e=>setSetType(e.target.value as SetType)}>{setTypes.map(type=><option key={type}>{type}</option>)}</select></label><label>Context 상태<select value={contextStatus} onChange={e=>setContextStatus(e.target.value as 'PARTIAL'|'LIVING'|'COMPLETE')}><option>PARTIAL</option><option>LIVING</option><option>COMPLETE</option></select></label></div>
      <label>학생용 문제지 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setStudentPaper)}/></label>
      <label>정답·해설 HTML<input type="file" accept=".html,.htm,text/html" onChange={e=>readFile(e,setAnswerExplanation)}/></label>
      <label>제작·검수 MD<input type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" onChange={e=>readFile(e,setVerification)}/></label>
      <p className="field-hint">세 파일은 하나의 세트로 저장됩니다. 서버가 현재 Living Context Snapshot을 보존하고 상태를 DRAFT로 고정합니다.</p>
      <button className="primary-action" disabled={!studentPaper||!answerExplanation||!verification} onClick={()=>void register()}>DRAFT 등록</button>
    </section>}
    <p className="approval-gate-note">원문은 운영자 전용입니다. Agent 등록은 DRAFT이며 운영자가 FINAL을 확정합니다. FINAL은 외부 공개를 의미하지 않습니다.</p>
  </section>
}
