import { ChangeEvent, useEffect, useMemo, useRef, useState } from 'react'
import JSZip from 'jszip'
import { extractFiles, joinExtractedPages } from '../lib/ocrExtract'

type Grade = '중1 (2022 개정)' | '중2 (2022 개정)' | '중3 (2022 개정)'
type Material = { id: string; title: string; publisher: string; grade: Grade; lesson: string; tags: string; author: string; fileName: string; size: string; createdAt: string; content?: string }
const publishers = ['NE능률(김기택)', 'YBM(김은형)', 'YBM(박준언)', '동아(윤정미)', '동아(이병민)', '미래엔(문영인)', '비상(황종배)', '지학사(송미정)', '천재(소영순)', '천재(이상기)']
const lessons = ['1과', '2과', '3과', '4과', '5과', '6과', '7과', '8과', 'Special Lesson', 'Special Reading 1', 'Special Reading 2']
const allowedExtensions = ['pdf', 'hwp', 'hwpx', 'docx', 'ppt', 'pptx', 'png', 'jpg', 'jpeg', 'zip']
const loadMaterials = (): Material[] => { try { return JSON.parse(localStorage.getItem('f2r_textbook_materials') || '[]') as Material[] } catch { return [] } }
const fileSize = (file: File) => `${Math.max(1, Math.round(file.size / 1024))}KB`

const DB_NAME = 'f2r_textbook_files'
const STORE = 'files'
const openFileDb = () => new Promise<IDBDatabase>((resolve, reject) => {
  const request = indexedDB.open(DB_NAME, 1)
  request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE) }
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})
const putFile = async (id: string, file: File) => { const db = await openFileDb(); await new Promise<void>((resolve, reject) => { const tx = db.transaction(STORE, 'readwrite'); tx.objectStore(STORE).put(file, id); tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error) }); db.close() }
const getFile = async (id: string) => { const db = await openFileDb(); const file = await new Promise<File | undefined>((resolve, reject) => { const req = db.transaction(STORE).objectStore(STORE).get(id); req.onsuccess = () => resolve(req.result as File | undefined); req.onerror = () => reject(req.error) }); db.close(); return file }
const deleteFile = async (id: string) => { const db = await openFileDb(); await new Promise<void>((resolve, reject) => { const tx = db.transaction(STORE, 'readwrite'); tx.objectStore(STORE).delete(id); tx.oncomplete = () => resolve(); tx.onerror = () => reject(tx.error) }); db.close() }
const escapeHtml = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const plainToHtml = (value: string) => value.split(/\n{2,}/).map(block => `<p>${escapeHtml(block).replace(/\n/g, '<br>')}</p>`).join('')
const xmlNodesToHtml = (xml: string, textTag: string, paragraphTag: string) => {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  const paragraphs = Array.from(doc.getElementsByTagName(paragraphTag))
  if (!paragraphs.length) return plainToHtml(Array.from(doc.getElementsByTagName(textTag)).map(n => n.textContent || '').join(' '))
  return paragraphs.map(p => {
    const text = Array.from(p.getElementsByTagName(textTag)).map(n => n.textContent || '').join('')
    return text.trim() ? `<p>${escapeHtml(text)}</p>` : '<p><br></p>'
  }).join('')
}
const extractOfficeHtml = async (file: File) => {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (ext === 'docx' || ext === 'pptx' || ext === 'hwpx') {
    const zip = await JSZip.loadAsync(file)
    if (ext === 'docx') {
      const entry = zip.file('word/document.xml')
      return entry ? xmlNodesToHtml(await entry.async('string'), 'w:t', 'w:p') : ''
    }
    if (ext === 'pptx') {
      const names = Object.keys(zip.files).filter(name => /^ppt\/slides\/slide\d+\.xml$/.test(name)).sort()
      const parts: string[] = []
      for (const name of names) { const entry = zip.file(name); if (entry) parts.push(xmlNodesToHtml(await entry.async('string'), 'a:t', 'a:p')) }
      return parts.join('<hr>')
    }
    const names = Object.keys(zip.files).filter(name => /^(Contents|contents)\/section\d+\.xml$/.test(name)).sort()
    const parts: string[] = []
    for (const name of names) {
      const entry = zip.file(name)
      if (!entry) continue
      const xml = await entry.async('string')
      const doc = new DOMParser().parseFromString(xml, 'application/xml')
      const paras = Array.from(doc.getElementsByTagName('*')).filter(n => n.localName === 'p')
      parts.push(paras.map(p => {
        const text = Array.from(p.getElementsByTagName('*')).filter(n => n.localName === 't').map(n => n.textContent || '').join('')
        return text.trim() ? `<p>${escapeHtml(text)}</p>` : '<p><br></p>'
      }).join(''))
    }
    return parts.join('')
  }
  if (['pdf', 'png', 'jpg', 'jpeg'].includes(ext || '')) return plainToHtml(joinExtractedPages(await extractFiles([file])))
  return ''
}

export default function TextbookDatabase() {
  const [grade, setGrade] = useState<Grade | null>(null)
  const [publisher, setPublisher] = useState('')
  const [lesson, setLesson] = useState('')
  const [materials, setMaterials] = useState<Material[]>(loadMaterials)
  const [notice, setNotice] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [form, setForm] = useState({ title: '', tags: '', author: '박정민 원장' })
  const [editing, setEditing] = useState<Material | null>(null)
  const [editHtml, setEditHtml] = useState('')
  const editorRef = useRef<HTMLDivElement>(null)
  const [editBusy, setEditBusy] = useState(false)
  const [editorMode, setEditorMode] = useState<'analysis' | 'exam'>('analysis')
  const [printMeta, setPrintMeta] = useState({ title: '영어 내신 학습자료', academy: 'Feel to Real 영어학원', director: '박정민 원장' })
  const disabled = grade === '중3 (2022 개정)'
  const filtered = useMemo(() => materials.filter(item => item.grade === grade && item.publisher === publisher && item.lesson === lesson), [materials, grade, publisher, lesson])
  const persist = (next: Material[]) => { setMaterials(next); localStorage.setItem('f2r_textbook_materials', JSON.stringify(next)) }
  const chooseGrade = (next: Grade) => { setGrade(next); setPublisher(''); setLesson(''); setNotice(next === '중3 (2022 개정)' ? '중3 2022 개정 교과서는 2027년 제공 예정입니다.' : '') }
  const chooseFiles = (event: ChangeEvent<HTMLInputElement>) => { const selected = Array.from(event.target.files || []); const valid = selected.filter(file => allowedExtensions.includes(file.name.split('.').pop()?.toLowerCase() || '')); const invalidCount = selected.length - valid.length; if (!valid.length) { setNotice('지원하는 자료 파일을 선택해 주세요.'); return }; setFiles(current => [...current, ...valid.filter(file => !current.some(saved => saved.name === file.name && saved.size === file.size && saved.lastModified === file.lastModified))]); event.target.value = ''; setNotice(invalidCount ? `지원하지 않는 파일 ${invalidCount}개를 제외했습니다.` : `${valid.length}개 파일을 선택했습니다.`) }
  const addMaterial = async () => { if (!grade || !publisher || !lesson || !files.length) { setNotice('학년, 출판사, Lesson과 파일을 확인해 주세요.'); return }; const createdAt = new Date().toLocaleString('ko-KR'); const next: Material[] = []; for (const file of files) { const id = crypto.randomUUID(); await putFile(id, file); next.push({ id, title: form.title.trim() ? (files.length === 1 ? form.title.trim() : `${form.title.trim()} · ${file.name}`) : file.name.replace(/\.[^.]+$/, ''), publisher, grade, lesson, tags: form.tags.trim(), author: form.author.trim() || '작성자 미입력', fileName: file.name, size: fileSize(file), createdAt }) }; persist([...next, ...materials]); setForm({ title: '', tags: '', author: '박정민 원장' }); setFiles([]); setNotice(`${next.length}개 자료를 등록했습니다.`) }
  const removeMaterial = async (id: string) => { if (!window.confirm('이 자료를 삭제할까요?')) return; await deleteFile(id).catch(() => undefined); persist(materials.filter(item => item.id !== id)); if (editing?.id === id) setEditing(null) }
  const downloadMaterial = async (item: Material) => { const file = await getFile(item.id); if (!file) { setNotice('이 자료는 이전 버전에서 파일 원본 없이 등록되어 있습니다. 원본을 다시 등록하면 다운로드할 수 있습니다.'); return }; const url = URL.createObjectURL(file); const a = document.createElement('a'); a.href = url; a.download = item.fileName; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
  const openEditor = async (item: Material) => {
    setEditing(item); setEditHtml(item.content || ''); setEditBusy(true)
    try {
      if (!item.content) {
        const file = await getFile(item.id)
        if (!file) setNotice('이전 버전 자료라 원본 파일이 없습니다. 파일을 다시 등록하면 내용 추출 편집이 가능합니다.')
        else {
          const ext = file.name.split('.').pop()?.toLowerCase()
          const html = await extractOfficeHtml(file)
          if (html) setEditHtml(html)
          else if (ext === 'hwp') setEditHtml('<p><strong>HWP 원본</strong>은 브라우저만으로 안정적으로 해석할 수 없습니다. HWPX로 저장한 뒤 다시 등록하면 문단을 HTML 편집기로 불러올 수 있습니다.</p>')
          else setEditHtml('<p>이 파일에서는 편집 가능한 본문을 추출하지 못했습니다.</p>')
        }
      }
    } catch (error) { setNotice(`내용을 불러오지 못했습니다: ${error instanceof Error ? error.message : '알 수 없는 오류'}`) }
    finally { setEditBusy(false); setTimeout(() => document.getElementById('textbook-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50) }
  }
  useEffect(() => { if (editing && editorRef.current && editorRef.current.innerHTML !== editHtml) editorRef.current.innerHTML = editHtml }, [editing, editHtml])
  const saveEdit = () => {
    if (!editing) return
    const html = editorRef.current?.innerHTML || editHtml
    const next = materials.map(item => item.id === editing.id ? { ...item, content: html } : item)
    persist(next); setEditHtml(html); setEditing({ ...editing, content: html }); setNotice('HTML 편집 내용을 저장했습니다.')
  }
  const runEditorCommand = (command: string, value?: string) => {
    editorRef.current?.focus(); document.execCommand(command, false, value); setEditHtml(editorRef.current?.innerHTML || '')
  }
  const currentEditorHtml = () => editorRef.current?.innerHTML || editHtml
  const insertHtml = (html: string) => { editorRef.current?.focus(); document.execCommand('insertHTML', false, html); setEditHtml(currentEditorHtml()) }
  const buildAnalysisTemplate = () => {
    const text = (editorRef.current?.innerText || '').trim()
    const lines = text.split(/(?<=[.!?])\s+|\n+/).map(v => v.trim()).filter(Boolean)
    const html = (lines.length ? lines : ['영어 문장을 여기에 입력하세요.']).map((line, i) => `<section class="analysis-block"><p class="analysis-source"><b>${i + 1}.</b> ${escapeHtml(line)}</p><p class="analysis-translation">해석: </p><p class="analysis-note"><b>구문·문법 분석</b> · 핵심 표현과 문법 설명을 입력하세요.</p></section>`).join('')
    setEditHtml(html); setTimeout(() => { if (editorRef.current) editorRef.current.innerHTML = html }, 0)
  }
  const buildExamTemplate = () => {
    const source = (editorRef.current?.innerText || '').trim()
    const html = `<section class="exam-question"><h3>1. 다음 글을 읽고 물음에 답하시오.</h3><div class="exam-passage">${source ? escapeHtml(source).replace(/\n/g, '<br>') : '영어 지문을 입력하세요.'}</div><div class="exam-options">① 선택지 1<br>② 선택지 2<br>③ 선택지 3<br>④ 선택지 4<br>⑤ 선택지 5</div></section>`
    setEditHtml(html); setTimeout(() => { if (editorRef.current) editorRef.current.innerHTML = html }, 0)
  }
  const printableDocument = () => {
    const body = currentEditorHtml()
    return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>${escapeHtml(printMeta.title)}</title><style>@page{size:A4;margin:14mm}*{box-sizing:border-box}body{font-family:'Malgun Gothic','맑은 고딕',sans-serif;color:#111;font-size:10pt;line-height:1.55;margin:0}.print-head{text-align:center;border-bottom:1px solid #222;padding-bottom:8px;margin-bottom:14px}.print-head h1{font-size:16pt;margin:0 0 6px}.print-head p{margin:0;font-size:10pt}.print-body.exam{column-count:2;column-gap:12mm;column-rule:1px solid #bbb}.exam-question,.analysis-block{break-inside:avoid;margin:0 0 14px}.exam-question h3{font-size:10pt;margin:0 0 8px}.exam-passage{border:1px solid #777;padding:8px;margin:0 0 8px}.exam-options{line-height:1.9}.analysis-source{font-size:11pt;margin:0 0 5px}.analysis-translation{margin:0 0 5px}.analysis-note{border-left:3px solid #777;padding-left:8px;margin:0}table{border-collapse:collapse;width:100%}td,th{border:1px solid #777;padding:5px}img{max-width:100%}</style></head><body><header class="print-head"><h1>${escapeHtml(printMeta.title)}</h1><p>${escapeHtml(printMeta.academy)} · ${escapeHtml(printMeta.director)}</p></header><main class="print-body ${editorMode === 'exam' ? 'exam' : 'analysis'}">${body}</main></body></html>`
  }
  const printHtml = () => { const w = window.open('', '_blank'); if (!w) { setNotice('팝업이 차단되었습니다. 인쇄 창을 허용해 주세요.'); return }; w.document.open(); w.document.write(printableDocument()); w.document.close(); w.focus(); setTimeout(() => w.print(), 250) }
  const downloadHtml = () => { const blob = new Blob([printableDocument()], { type: 'text/html;charset=utf-8' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `${printMeta.title || '영어자료'}.html`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }

  return <section className="twin-dashboard" aria-label="영어 내신 교과서 DB">
    <div className="panel-heading"><div><p className="kicker">ENGLISH TEXTBOOK DATABASE</p><h2>영어 내신 교과서 DB</h2><p>학년, 출판사, Lesson별로 수업 자료를 정리하고 연결합니다.</p></div><span className="context-chip">Lesson 단위 관리</span></div>
    {notice && <div className="tool-notice" role="status">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    <div className="grade-cards">{(['중1 (2022 개정)', '중2 (2022 개정)', '중3 (2022 개정)'] as Grade[]).map(item => <button key={item} onClick={() => chooseGrade(item)} className={grade === item ? 'selected' : ''}><b>{item}</b><span>{item === '중3 (2022 개정)' ? '2027년 제공 예정' : '출판사와 Lesson 선택'}</span>{item === '중3 (2022 개정)' && <em>준비 중</em>}</button>)}</div>
    {grade && !disabled && <><article className="work-panel"><div className="panel-heading"><div><p className="kicker">TEXTBOOK PATH</p><h2>{grade} 교재 선택</h2></div></div><div className="publisher-grid">{publishers.map(item => <button key={item} className={publisher === item ? 'active' : ''} onClick={() => { setPublisher(item); setLesson('') }}>{item}</button>)}</div>{publisher && <div className="lesson-row">{lessons.map(item => <button key={item} className={lesson === item ? 'active' : ''} onClick={() => setLesson(item)}>{item}</button>)}</div>}</article>
    {publisher && lesson && <><article className="work-panel"><div className="panel-heading"><div><p className="kicker">LESSON MATERIAL UPLOAD</p><h2>{publisher} · {lesson} 자료 등록</h2><p>자료 제목·태그·작성자와 원본 파일을 함께 저장합니다.</p></div></div><div className="metadata-grid"><label>자료 제목<input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="예: 2과 본문 분석 자료" /></label><label>태그<input value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} placeholder="본문, 문법, 어휘" /></label><label>작성자<input value={form.author} onChange={e => setForm({ ...form, author: e.target.value })} /></label></div><div className="upload-row"><label className="file-picker">자료 파일 추가<input type="file" multiple accept=".pdf,.hwp,.hwpx,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.zip" onChange={chooseFiles} /></label><span>PDF · HWP · HWPX · DOCX · PPT · PPTX · PNG · JPG · ZIP</span></div>{files.length > 0 && <div className="selected-file-list"><b>선택한 파일 {files.length}개</b>{files.map((file, index) => <div key={`${file.name}-${index}`}><span>{file.name} · {fileSize(file)}</span><button onClick={() => setFiles(current => current.filter((_, i) => i !== index))}>삭제</button></div>)}</div>}<button className="primary-action" onClick={addMaterial}>선택한 자료 {files.length ? `${files.length}개 ` : ''}등록</button></article>
    <article className="work-panel"><div className="panel-heading"><div><p className="kicker">MATERIAL LIST</p><h2>등록 자료 {filtered.length}개</h2></div></div>{filtered.length ? <div className="material-list">{filtered.map(item => <article key={item.id}><div><b>{item.title}</b><p>{item.fileName} · {item.size} · {item.createdAt}</p><small>{item.tags || '태그 없음'} · 작성자 {item.author}</small></div><button onClick={() => downloadMaterial(item)}>다운로드</button><button onClick={() => openEditor(item)}>편집</button><button onClick={() => removeMaterial(item.id)}>삭제</button></article>)}</div> : <div className="empty-state"><b>등록된 자료가 없습니다.</b></div>}</article></>}</>}
    {editing && <article id="textbook-editor" className="work-panel textbook-editor"><div className="panel-heading"><div><p className="kicker">HTML MATERIAL EDITOR</p><h2>{editing.title} 편집</h2><p>문장 분석 자료 또는 A4 문제지를 HTML에서 직접 편집하고 그대로 인쇄할 수 있습니다.</p></div><button className="secondary-button" onClick={() => setEditing(null)}>편집 닫기</button></div>
    <div className="editor-mode-tabs"><button className={editorMode === 'analysis' ? 'active' : ''} onClick={() => setEditorMode('analysis')}>문장 분석편집</button><button className={editorMode === 'exam' ? 'active' : ''} onClick={() => setEditorMode('exam')}>문제 제작편집</button></div>
    <div className="print-meta-grid"><label>자료/시험지 제목<input value={printMeta.title} onChange={e => setPrintMeta({...printMeta,title:e.target.value})}/></label><label>학원명<input value={printMeta.academy} onChange={e => setPrintMeta({...printMeta,academy:e.target.value})}/></label><label>원장명<input value={printMeta.director} onChange={e => setPrintMeta({...printMeta,director:e.target.value})}/></label></div>
    <div className="textbook-html-toolbar"><button onMouseDown={e => { e.preventDefault(); runEditorCommand('bold') }}><b>B</b></button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('underline') }}><u>U</u></button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('hiliteColor','#fff3a3') }}>형광펜</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('foreColor','#1677d2') }}>파란글씨</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('insertUnorderedList') }}>• 목록</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('insertOrderedList') }}>1. 목록</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('justifyLeft') }}>왼쪽</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('justifyCenter') }}>가운데</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('undo') }}>↶</button><button onMouseDown={e => { e.preventDefault(); runEditorCommand('redo') }}>↷</button><button onClick={() => insertHtml('<br><br>')}>줄 간격</button>{editorMode === 'analysis' ? <button className="template-action" onClick={buildAnalysisTemplate}>문장 분석 틀 만들기</button> : <button className="template-action" onClick={buildExamTemplate}>문제 틀 만들기</button>}</div>
    <div className={`textbook-editor-full ${editorMode}`}><div className="editor-print-preview-head"><b>{printMeta.title}</b><span>{printMeta.academy} · {printMeta.director}</span></div><div ref={editorRef} className={`textbook-rich-editor ${editorMode === 'exam' ? 'exam-editor' : 'analysis-editor'}`} contentEditable={!editBusy} suppressContentEditableWarning onInput={e => setEditHtml(e.currentTarget.innerHTML)} data-placeholder={editBusy ? '원본에서 내용을 추출하는 중입니다…' : editorMode === 'analysis' ? '영어 문장을 붙여넣은 뒤 문장 분석 틀 만들기를 누르세요.' : '영어 지문을 붙여넣은 뒤 문제 틀 만들기를 누르세요.'} /></div>
    <div className="editor-output-actions"><button className="primary-action" onClick={saveEdit} disabled={editBusy}>수정 내용 저장</button><button className="secondary-button" onClick={printHtml}>인쇄 / PDF 저장</button><button className="secondary-button" onClick={downloadHtml}>HTML 다운로드</button></div></article>}
  </section>
}
