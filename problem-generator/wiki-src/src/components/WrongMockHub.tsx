import { ChangeEvent, useEffect, useState } from 'react'
import { api, apiUrl } from '../lib/api'
import type { Page } from '../App'

type ExamContext = { school:string; grade:string; year:number; semester:string; exam:string }
type Photo = {
  id:number; round_id:number; title:string; original_name:string; original_type:string
  size:number; content_hash:string; sort_order:number; uploaded_by:string; created_at:string
  original_api_path:string
}
type Round = {
  id:number; school:string; grade:string; year:number; semester:string; exam:string
  round:number; title:string; question_count:number; status:'COLLECTING'|'READY'|'COMPLETED'
  status_label:string; photo_count:number; has_paper:boolean; has_explanation:boolean
  paper_name:string; expl_name:string; edit_locked:boolean
  created_by:string; created_at:string; updated_at:string
  photos?:Photo[]
}
type Props = { context:ExamContext; onNavigate:(page:Page)=>void }

const query = (context:ExamContext) => new URLSearchParams({...context, year:String(context.year)}).toString()
const readFile = (event:ChangeEvent<HTMLInputElement>, setter:(value:string)=>void) => {
  const file = event.target.files?.[0]; if (!file) return
  const reader = new FileReader(); reader.onload = () => setter(String(reader.result || '')); reader.readAsText(file)
}
const apiError = async (response:Response, fallback:string) => {
  const body = await response.json().catch(() => ({}))
  const detail = (body as {detail?:unknown}).detail
  return typeof detail === 'string' && detail ? detail : fallback
}

type PendingUpload = { files:File[]; titles:string[] }

export default function WrongMockHub({context, onNavigate}:Props) {
  const [rounds, setRounds] = useState<Round[]>([])
  const [detail, setDetail] = useState<Round|null>(null)
  const [notice, setNotice] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [newRound, setNewRound] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [confirmDeleteId, setConfirmDeleteId] = useState<number|null>(null)
  const [staged, setStaged] = useState<{file:File; title:string}[]>([])
  const [pendingForce, setPendingForce] = useState<PendingUpload|null>(null)
  const [renameId, setRenameId] = useState<number|null>(null)
  const [renameTitle, setRenameTitle] = useState('')
  const [replaceId, setReplaceId] = useState<number|null>(null)
  const [paperHtml, setPaperHtml] = useState('')
  const [explHtml, setExplHtml] = useState('')

  const nextRound = rounds.length ? Math.max(...rounds.map(r => r.round)) + 1 : 1

  const load = async () => {
    if (!context.school || !context.grade) return
    const response = await api(`wrong-mock/rounds?${query(context)}`)
    if (response.ok) setRounds(await response.json())
  }
  useEffect(() => { setDetail(null); void load() },
    [context.school, context.grade, context.year, context.semester, context.exam])

  const openDetail = async (id:number) => {
    const response = await api(`wrong-mock/rounds/${id}`)
    if (!response.ok) { setNotice(await apiError(response, '회차를 불러오지 못했습니다.')); return }
    const row = await response.json()
    setDetail(row)
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
  }
  const refreshDetail = async (id:number) => {
    const response = await api(`wrong-mock/rounds/${id}`)
    if (response.ok) {
      const row = await response.json()
      setDetail(row)
      setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    }
  }

  const create = async () => {
    const round = newRound.trim() ? Number(newRound) : nextRound
    const response = await api('wrong-mock/rounds', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({...context, round, title:newTitle.trim()})})
    if (!response.ok) { setNotice(await apiError(response, '회차를 만들지 못했습니다.')); return }
    const row = await response.json()
    setNewRound(''); setNewTitle(''); setShowCreate(false)
    setNotice(`오답 ${row.round}회를 만들었습니다. 사진을 등록해 주세요.`)
    await load(); await openDetail(row.id)
  }

  const stageFiles = (event:ChangeEvent<HTMLInputElement>) => {
    const chosen = Array.from(event.target.files || []); event.target.value = ''
    if (!chosen.length || !detail) return
    setPendingForce(null)
    setStaged(chosen.map(file => ({file, title:file.name})))
  }

  const uploadStaged = async (force:boolean) => {
    if (!detail || !staged.length) return
    const form = new FormData()
    staged.forEach(item => form.append('files', item.file))
    form.append('titles', JSON.stringify(staged.map(item => item.title)))
    form.append('force', String(force))
    const response = await api(`wrong-mock/rounds/${detail.id}/photos`, {method:'POST', body:form})
    if (!response.ok) {
      const message = await apiError(response, '사진을 등록하지 못했습니다.')
      if (response.status === 409 && message.includes('계속 등록하시겠습니까')) {
        setPendingForce({files:staged.map(item => item.file), titles:staged.map(item => item.title)})
        setNotice(message)
        return
      }
      setNotice(message); return
    }
    const row = await response.json()
    setDetail(row); setStaged([]); setPendingForce(null)
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    setNotice(`사진 ${row.photo_count}장 등록됨 · ${row.title}`)
  }

  const confirmForceUpload = async () => {
    if (!detail || !pendingForce) return
    const form = new FormData()
    pendingForce.files.forEach(file => form.append('files', file))
    form.append('titles', JSON.stringify(pendingForce.titles))
    form.append('force', 'true')
    const response = await api(`wrong-mock/rounds/${detail.id}/photos`, {method:'POST', body:form})
    if (!response.ok) { setNotice(await apiError(response, '사진을 등록하지 못했습니다.')); return }
    const row = await response.json()
    setDetail(row); setStaged([]); setPendingForce(null)
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    setNotice(`다른 회차 중복 사진을 등록했습니다 · ${row.title}`)
  }

  const rename = async (photo:Photo) => {
    if (!detail) return
    const response = await api(`wrong-mock/photos/${photo.id}`, {method:'PATCH',
      headers:{'Content-Type':'application/json'}, body:JSON.stringify({title:renameTitle})})
    if (!response.ok) { setNotice(await apiError(response, '제목을 수정하지 못했습니다.')); return }
    setRenameId(null); setRenameTitle('')
    setNotice('사진 제목을 수정했습니다.'); await refreshDetail(detail.id)
  }

  const move = async (photoIds:number[], id:number, offset:number) => {
    if (!detail) return
    const index = photoIds.indexOf(id), target = index + offset
    if (index < 0 || target < 0 || target >= photoIds.length) return
    const ordered = [...photoIds]; [ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    const response = await api(`wrong-mock/rounds/${detail.id}/photos/reorder`, {method:'POST',
      headers:{'Content-Type':'application/json'}, body:JSON.stringify({orderedIds:ordered})})
    if (!response.ok) { setNotice(await apiError(response, '순서를 바꾸지 못했습니다.')); return }
    await refreshDetail(detail.id)
  }

  const removePhoto = async (photo:Photo) => {
    if (!detail || !window.confirm(`"${photo.title || photo.original_name}" 사진을 삭제할까요?`)) return
    const response = await api(`wrong-mock/photos/${photo.id}`, {method:'DELETE'})
    if (!response.ok) { setNotice(await apiError(response, '사진을 삭제하지 못했습니다.')); return }
    setNotice('사진을 삭제했습니다.'); await refreshDetail(detail.id)
  }

  const replacePhoto = async (photo:Photo, event:ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]; event.target.value = ''
    if (!file || !detail) return
    const form = new FormData(); form.append('file', file)
    const response = await api(`wrong-mock/photos/${photo.id}`, {method:'PUT', body:form})
    if (!response.ok) { setNotice(await apiError(response, '사진을 교체하지 못했습니다.')); return }
    setReplaceId(null); setNotice('사진을 교체했습니다.'); await refreshDetail(detail.id)
  }

  const completePhotos = async () => {
    if (!detail) return
    if (!window.confirm(`${detail.title} 사진 등록을 완료하고 제작대기로 넘길까요?`)) return
    const response = await api(`wrong-mock/rounds/${detail.id}/complete-photos`, {method:'POST'})
    if (!response.ok) { setNotice(await apiError(response, '제작대기로 넘기지 못했습니다.')); return }
    const row = await response.json()
    setDetail(row)
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    setNotice(`${row.title} · 제작대기 — 편집이 잠겼습니다.`)
  }

  const registerArtifacts = async () => {
    if (!detail || (!paperHtml.trim() && !explHtml.trim())) return
    const response = await api(`wrong-mock/rounds/${detail.id}/artifacts`, {method:'PUT',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({paperHtml:paperHtml.trim() || undefined, explHtml:explHtml.trim() || undefined})})
    if (!response.ok) { setNotice(await apiError(response, '자료를 등록하지 못했습니다.')); return }
    const row = await response.json()
    setDetail(row); setPaperHtml(''); setExplHtml('')
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    setNotice(row.status === 'COMPLETED' ? `${row.title} · 제작완료` : `${row.title} · 자료를 등록했습니다.`)
  }

  const toggleLock = async () => {
    if (!detail) return
    const response = await api(`wrong-mock/rounds/${detail.id}/edit-lock`, {method:'POST',
      headers:{'Content-Type':'application/json'}, body:JSON.stringify({locked:!detail.edit_locked})})
    if (!response.ok) { setNotice(await apiError(response, '편집 상태를 바꾸지 못했습니다.')); return }
    const row = await response.json()
    setDetail(row)
    setRounds(items => items.map(item => item.id === row.id ? {...item, ...row, photos: undefined} : item))
    setNotice(row.edit_locked ? '편집 OFF — 완성본을 보호합니다.' : '편집 ON — 수정할 수 있습니다.')
  }

  const removeRound = async (id:number) => {
    const response = await api(`wrong-mock/rounds/${id}`, {method:'DELETE'})
    if (!response.ok) { setNotice(await apiError(response, '회차를 삭제하지 못했습니다.')); return }
    setConfirmDeleteId(null); setDetail(null)
    setNotice('회차를 삭제했습니다.'); await load()
  }

  const open = (path:string) => window.open(apiUrl(path), '_blank', 'noopener')
  const statusClass = (status:Round['status']) =>
    status === 'COMPLETED' ? 'completed' : status === 'READY' ? 'ready' : 'collecting'
  const editable = detail && (detail.status === 'COLLECTING' || !detail.edit_locked)

  return <section className="wrong-mock-hub" id="wrong-mock-hub" aria-label="오답 모의고사">
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    <div className="panel-heading"><div>
      <p className="kicker">WRONG MOCK · 20 QUESTIONS</p><h2>오답 모의고사</h2>
      <p>회차를 먼저 만들고 20문항 사진을 직접 등록합니다. 사진 등록 완료 → 제작대기 → 문제지·간략해설 등록 → 제작완료.</p>
    </div></div>
    <div className="twin-actions">
      <button type="button" className="primary-action" onClick={() => { setShowCreate(v => !v); setNewRound(''); setNewTitle('') }}>+ 새 회차</button>
      <button type="button" className="secondary-button" onClick={() => onNavigate('wrongnote')}>학생 오답 기록 열기</button>
      <button type="button" className="secondary-button" onClick={() => onNavigate('wrongrestore')}>오답 사진 AI 복원 열기</button>
    </div>

    {showCreate && <div className="work-panel wrong-mock-create" aria-label="새 회차 만들기">
      <label>회차<input type="number" min="1" value={newRound} placeholder={String(nextRound)} onChange={e => setNewRound(e.target.value)} /></label>
      <label>제목<input value={newTitle} placeholder={`오답 ${newRound.trim() || nextRound}회`} onChange={e => setNewTitle(e.target.value)} /></label>
      <p className="field-hint">비워 두면 마지막 회차 다음 번호({nextRound}회)로 만듭니다. 같은 시험 Context에 같은 회차가 있으면 만들 수 없습니다.</p>
      <button type="button" className="primary-action" onClick={() => void create()}>회차 만들기</button>
    </div>}

    {rounds.length === 0
      ? <div className="empty-state"><b>아직 오답 모의고사 회차가 없습니다.</b><p>+ 새 회차로 오답 24회를 만들어 사진을 등록하세요.</p></div>
      : <div className="wrong-mock-grid">{rounds.map(item => <article key={item.id} className="wrong-mock-card">
        <div><b>{item.title}</b><span className={`status-badge ${statusClass(item.status)}`}>{item.status_label}</span>
          <small>{item.question_count}문항 · 사진 {item.photo_count}장{!item.photo_count && item.status === 'COLLECTING' ? ' · 자료 등록 중' : ''}{item.photo_count > 0 && item.status === 'COLLECTING' ? ' · 등록된 자료' : ''}</small></div>
        <div className="artifact-actions">
          {item.has_paper && <button type="button" onClick={() => open(`wrong-mock/rounds/${item.id}/artifacts/paper`)}>문제지 보기</button>}
          {item.has_explanation && <button type="button" onClick={() => open(`wrong-mock/rounds/${item.id}/artifacts/explanation`)}>간략해설 보기</button>}
          <button type="button" onClick={() => void openDetail(item.id)}>{item.status === 'COLLECTING' ? '사진 추가 · 등록 자료 보기' : '원본 사진 · 상세 보기'}</button>
          <button type="button" disabled>{item.edit_locked ? '편집 OFF' : '편집 ON'}</button>
        </div>
      </article>)}</div>}

    {detail && <section className="work-panel wrong-mock-detail" aria-label={`${detail.title} 상세`}>
      <div className="panel-heading"><div>
        <p className="kicker">ROUND DETAIL</p><h2>{detail.title}</h2>
        <p>{detail.question_count}문항 · {detail.status_label} · 사진 {(detail.photos || []).length}장 · {detail.edit_locked ? '편집 OFF' : '편집 ON'}</p>
      </div><button type="button" onClick={() => { setDetail(null); setStaged([]); setPendingForce(null) }}>닫기</button></div>

      <div className="approval-actions">
        {detail.status === 'COLLECTING'
          ? <><button type="button" className="primary-action" onClick={() => void completePhotos()}>사진 등록 완료 → 제작대기</button>
            {confirmDeleteId === detail.id
              ? <><span>정말 삭제할까요?</span><button type="button" onClick={() => void removeRound(detail.id)}>삭제 확인</button><button type="button" onClick={() => setConfirmDeleteId(null)}>취소</button></>
              : <button type="button" onClick={() => setConfirmDeleteId(detail.id)}>회차 삭제</button>}</>
          : <button type="button" onClick={() => void toggleLock()}>{detail.edit_locked ? '편집 ON으로 전환' : '편집 OFF로 전환'}</button>}
      </div>

      <h3>원본 오답 사진 {(detail.photos || []).length}장</h3>
      {(detail.photos || []).length === 0 && <p className="field-hint">아직 등록된 사진이 없습니다. 아래에서 이 회차에 쓸 20문항 사진을 올리세요.</p>}
      {(detail.photos || []).length > 0 && <div className="wrong-mock-photos">
        {detail.photos!.map(photo => <figure key={photo.id}>
          {/^image\//.test(photo.original_type) || /\.(jpg|jpeg|png|gif|webp)$/i.test(photo.original_name)
            ? <img src={apiUrl(`wrong-mock/photos/${photo.id}/original`)} alt={photo.title || photo.original_name} loading="lazy" />
            : <div className="wrong-mock-file-icon" aria-label={photo.original_name}>{photo.original_name}</div>}
          <figcaption><b>{photo.title || photo.original_name}</b></figcaption>
          {editable ? <div className="artifact-actions">
            {renameId === photo.id
              ? <><input aria-label="사진 제목" value={renameTitle} onChange={e => setRenameTitle(e.target.value)} />
                <button type="button" onClick={() => void rename(photo)}>저장</button>
                <button type="button" onClick={() => setRenameId(null)}>취소</button></>
              : <button type="button" onClick={() => { setRenameId(photo.id); setRenameTitle(photo.title) }}>제목 수정</button>}
            <button type="button" aria-label={`${photo.title || '사진'} 앞으로`} onClick={() => void move(detail.photos!.map(p => p.id), photo.id, -1)}>앞으로</button>
            <button type="button" aria-label={`${photo.title || '사진'} 뒤로`} onClick={() => void move(detail.photos!.map(p => p.id), photo.id, 1)}>뒤로</button>
            {replaceId === photo.id
              ? <label>교체 파일 선택<input type="file" accept="image/*,.pdf" onChange={e => void replacePhoto(photo, e)} /></label>
              : <button type="button" onClick={() => setReplaceId(photo.id)}>교체</button>}
            <button type="button" onClick={() => void removePhoto(photo)}>삭제</button>
            <button type="button" onClick={() => open(`wrong-mock/photos/${photo.id}/original`)}>원본 보기</button>
          </div> : <div className="artifact-actions">
            <button type="button" onClick={() => open(`wrong-mock/photos/${photo.id}/original`)}>원본 보기</button>
          </div>}
        </figure>)}
      </div>}

      {editable ? <div className="wrong-mock-upload" aria-label="사진 등록">
        <label>사진 선택 (여러 장 가능)<input type="file" multiple accept="image/*,.pdf" onChange={stageFiles} /></label>
        {staged.length > 0 && <div className="selected-file-list"><b>등록할 사진 {staged.length}장 — 제목을 확인하세요</b>
          {staged.map((item, index) => <div key={`${item.file.name}-${index}`}>
            <span>{item.file.name}</span>
            <input aria-label={`${item.file.name} 제목`} value={item.title}
              onChange={e => setStaged(items => items.map((entry, i) => i === index ? {...entry, title:e.target.value} : entry))} />
            <button type="button" onClick={() => setStaged(items => items.filter((_, i) => i !== index))}>제외</button>
          </div>)}
          <button type="button" className="primary-action" onClick={() => void uploadStaged(false)}>사진 등록</button>
        </div>}
        {pendingForce && <div className="approval-actions">
          <span>다른 회차에도 있는 사진입니다. 계속 등록할까요?</span>
          <button type="button" className="primary-action" onClick={() => void confirmForceUpload()}>계속 등록</button>
          <button type="button" onClick={() => setPendingForce(null)}>취소</button>
        </div>}
        <p className="field-hint">사진 제목(출처·문제 정보)은 그대로 보존됩니다. 같은 회차 중복 사진은 등록되지 않습니다.</p>
      </div> : <p className="field-hint">편집이 잠겨 있습니다(편집 OFF). 사진을 바꾸려면 편집 ON으로 전환하세요.</p>}

      {detail.status !== 'COLLECTING' && <div className="wrong-mock-artifacts" aria-label="완성 artifact 등록">
        <h3>문제지 · 간략해설</h3>
        <div className="artifact-actions">
          {detail.has_paper && <><button type="button" onClick={() => open(`wrong-mock/rounds/${detail.id}/artifacts/paper`)}>문제지 보기</button>
            <button type="button" onClick={() => open(`wrong-mock/rounds/${detail.id}/artifacts/paper/download`)}>문제지 HTML 다운로드</button></>}
          {detail.has_explanation && <><button type="button" onClick={() => open(`wrong-mock/rounds/${detail.id}/artifacts/explanation`)}>간략해설 보기</button>
            <button type="button" onClick={() => open(`wrong-mock/rounds/${detail.id}/artifacts/explanation/download`)}>간략해설 HTML 다운로드</button></>}
        </div>
        {editable ? <>
          <label>문제지 HTML 파일<input type="file" accept=".html,.htm,text/html" onChange={e => readFile(e, setPaperHtml)} /></label>
          <label>간략해설 HTML 파일<input type="file" accept=".html,.htm,text/html" onChange={e => readFile(e, setExplHtml)} /></label>
          <p className="field-hint">두 파일이 모두 등록되면 제작완료가 됩니다. {detail.has_paper || detail.has_explanation ? '이미 등록된 파일은 필요한 것만 다시 올려 교체할 수 있습니다.' : ''}</p>
          <button type="button" className="primary-action" disabled={!paperHtml.trim() && !explHtml.trim()} onClick={() => void registerArtifacts()}>문제지·해설 등록</button>
        </> : <p className="field-hint">편집이 잠겨 있습니다(편집 OFF). 완성본을 교체하려면 편집 ON으로 전환하세요.</p>}
      </div>}
    </section>}
  </section>
}
