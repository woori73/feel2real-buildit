import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import App from './App'

const scrollIntoViewMock = vi.fn()

beforeEach(() => {
  const storage=()=>{const values=new Map<string,string>();return{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key),clear:()=>values.clear(),key:(index:number)=>[...values.keys()][index]??null,get length(){return values.size}}}
  vi.stubGlobal('localStorage',storage());vi.stubGlobal('sessionStorage',storage())
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url=String(input)
    if(url.endsWith('/students')) return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    if(url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
    return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
  }))
  scrollIntoViewMock.mockClear()
  window.HTMLElement.prototype.scrollIntoView = scrollIntoViewMock
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('내신 Wiki V1.1 시나리오 홈', () => {
  it('학교·학년·연도·학기·시험 Context와 STEP 1~4를 순서대로 표시한다', async()=>{
    render(<App/>)
    expect(await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})).toBeInTheDocument()
    expect(screen.getByLabelText('시험 Context')).toBeInTheDocument()
    for(const step of ['STEP 1','STEP 2','STEP 3','STEP 4']) {
      expect(screen.getByText(step)).toBeInTheDocument()
    }
    for(const name of ['학교 프린트 추가','학생 오답 추가','수업 인사이트 기록','문제 만들기','기존 문제 변형','저장된 문제','FINAL 준비','직보 준비']) {
      expect(screen.getByRole('button',{name:new RegExp(name)})).toBeInTheDocument()
    }
    expect(screen.queryByRole('button',{name:'Admin / Final 자료 보기'})).not.toBeInTheDocument()
  })

  it('STEP 1의 학생 오답 추가는 학생 선택·오답 원본·JSON 필드만 열고 자료 제목·구형 메타 입력이 없다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학생 오답 추가/}))
    const heading = screen.getByRole('heading',{name:'학생 오답 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    expect(form.getByLabelText(/^학생/)).toBeInTheDocument()
    expect(form.getByLabelText('오답 원본 파일')).toBeInTheDocument()
    expect(form.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(form.getByLabelText('또는 JSON 붙여넣기')).toBeInTheDocument()
    expect(form.queryByLabelText('자료 제목')).not.toBeInTheDocument()
    expect(form.queryByLabelText(/문제 유형|문제번호|시험명/)).not.toBeInTheDocument()
    const saveButton = form.getByRole('button', { name: '저장' })
    expect(saveButton).toBeDisabled()
    fireEvent.change(form.getByLabelText('오답 원본 파일'), { target: { files: [new File(['x'], 'wrong.jpg', { type: 'image/jpeg' })] } })
    expect(saveButton).toBeDisabled()
  })

  it('STEP 1의 학교 프린트 추가는 자료 제목·HTML·JSON(선택) 필드만 열고 원본 업로드가 없으며 HTML만으로 저장할 수 있다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const heading = screen.getByRole('heading',{name:'학교 프린트 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    expect(form.getByLabelText('자료 제목')).toBeInTheDocument()
    expect(form.getByLabelText('HTML 자료 파일 선택')).toBeInTheDocument()
    expect(form.getByLabelText('HTML 붙여넣기')).toBeInTheDocument()
    expect(form.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(form.queryByLabelText('원본 파일 업로드')).not.toBeInTheDocument()
    expect(form.queryByLabelText(/^학생/)).not.toBeInTheDocument()
    const saveButton = form.getByRole('button', { name: '저장' })
    expect(saveButton).toBeDisabled()
    fireEvent.change(form.getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    expect(saveButton).not.toBeDisabled()
  })

  it('학교 프린트 HTML 파일을 선택하면 브라우저에서 읽어 붙여넣기 textarea에 채운다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const heading = screen.getByRole('heading',{name:'학교 프린트 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    const file = new File(['<p>파일에서 읽은 HTML</p>'], 'print.html', { type: 'text/html' })
    fireEvent.change(form.getByLabelText('HTML 자료 파일 선택'), { target: { files: [file] } })
    await waitFor(() => expect((form.getByLabelText('HTML 붙여넣기') as HTMLTextAreaElement).value).toBe('<p>파일에서 읽은 HTML</p>'))
  })

  it('학교 프린트 추가 클릭 시 등록 폼이 열리고 즉시(behavior:auto) 그 위치까지 이동한다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const form = screen.getByRole('heading',{name:'학교 프린트 추가'}).closest('form') as HTMLElement
    expect(form).toBeInTheDocument()
    expect(form.id).toBe('material-set-form')
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled())
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(form)
    expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ behavior: 'auto', block: 'start' })
  })

  it('학생 오답 추가 클릭 시 등록 폼이 열리고 즉시(behavior:auto) 그 위치까지 이동한다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/학생 오답 추가/}))
    const form = screen.getByRole('heading',{name:'학생 오답 추가'}).closest('form') as HTMLElement
    expect(form).toBeInTheDocument()
    expect(form.id).toBe('material-set-form')
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled())
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(form)
    expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ behavior: 'auto', block: 'start' })
  })

  it('수업 인사이트 기록 버튼의 기존 스크롤 동작은 회귀 없이 그대로 유지된다', async()=>{
    render(<App/>)
    fireEvent.click(await screen.findByRole('button',{name:/수업 인사이트 기록/}))
    const insightSection = document.getElementById('insight-entry')
    expect(insightSection).toBeInTheDocument()
    expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth' })
    expect(scrollIntoViewMock.mock.instances[scrollIntoViewMock.mock.instances.length - 1]).toBe(insightSection)
  })

  it('버튼에 저장 건수를 "추가 · 저장 N건" 형식으로 표시한다', async()=>{
    render(<App/>)
    const printButton = await screen.findByRole('button',{name:/학교 프린트 추가\s*·\s*저장\s*0\s*건/})
    expect(printButton.textContent).toBe('학교 프린트 추가 · 저장 0건')
    const wrongButton = screen.getByRole('button',{name:/학생 오답 추가\s*·\s*저장\s*0\s*건/})
    expect(wrongButton.textContent).toBe('학생 오답 추가 · 저장 0건')
  })

  it('STEP 1에는 구형 도구(OCR·재구성, 오답 사진 추출, 학생별 오답노트) 안내가 없다', async()=>{
    render(<App/>)
    await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})
    expect(screen.queryByText(/OCR·재구성 도구/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/오답 사진 추출/})).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/학생별 오답노트/})).not.toBeInTheDocument()
  })

  it('시험 Context는 전체/중간/기말만 선택할 수 있고 기본값은 전체다', async()=>{
    render(<App/>)
    await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})
    const examSelect = within(screen.getByLabelText('시험 Context')).getByLabelText('시험') as HTMLSelectElement
    const optionLabels = [...examSelect.options].map(o => o.textContent)
    expect(optionLabels).toEqual(['전체', '중간', '기말'])
    expect(examSelect.value).toBe('전체')
  })

  it('학교 목록 조회가 늦게 끝나도 그 사이 사용자가 이미 고른 시험 Context는 전체로 되돌아가지 않는다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let resolveSchools: (value: Response) => void = () => {}
    const schoolsPromise = new Promise<Response>(resolve => { resolveSchools = resolve })
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/schools')) return schoolsPromise
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    // 학교 목록 응답이 아직 오지 않은 상태(운영 네트워크 지연 재현)에서 사용자가 먼저 시험을 바꾼다
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    // 뒤늦게 학교 목록 조회가 끝난다
    resolveSchools(new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    await screen.findByText('신사중 · 3학년')
    const examSelect = within(screen.getByLabelText('시험 Context')).getByLabelText('시험') as HTMLSelectElement
    expect(examSelect.value).toBe('중간고사')
  })

  it('학교 프린트 저장 후 동일한 5개 시험 Context query로 재조회해 저장 자료를 표시한다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let saved = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') {
        saved = true
        return new Response('{"id":7}', { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.includes('/exam-materials?')) return new Response(saved ? '[{"id":7,"core_type":"school_print","student_id":null,"student_name":null,"title":"5과 학교 프린트","original_name":"","created_at":"","has_html":1}]' : '[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    await screen.findByText(`${new Date().getFullYear()}년 · 1학기 · 중간`)
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    expect(within(formElement).getByText(/중간에 자동 연결됩니다/)).toBeInTheDocument()
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    fireEvent.submit(formElement)
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')).toBe(true))
    const call = fetchMock.mock.calls.find(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')
    const form = call?.[1]?.body as FormData
    expect(form.get('school')).toBe('신사중')
    expect(form.get('grade')).toBe('3학년')
    expect(form.get('exam')).toBe('중간고사')
    expect(form.get('html_text')).toBe('<p>5과 본문 HTML</p>')
    expect(form.get('original')).toBeNull()
    await screen.findByRole('button', { name: /학교 프린트 추가\s*·\s*저장\s*1\s*건/ })
    fireEvent.click(screen.getByRole('button', { name: 'FINAL 준비' }))
    await screen.findByText('5과 학교 프린트')
    const getCalls = fetchMock.mock.calls.filter(([u, init]) => String(u).includes('/exam-materials?') && !init?.method)
    const savedContextCall = getCalls[getCalls.length - 1]
    const query = new URL(String(savedContextCall[0]), 'https://feel2real.co.kr').searchParams
    expect(Object.fromEntries(query)).toEqual({ school: '신사중', grade: '3학년', year: String(new Date().getFullYear()), semester: '1학기', exam: '중간고사' })
  })

  it('HTML 붙여넣기와 JSON 파일 선택 후 저장 버튼 click이 POST를 만들고 동일 Context의 저장 자료를 표시한다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    let saved = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') {
        saved = true
        return new Response('{"id":8}', { status: 201, headers: { 'Content-Type': 'application/json' } })
      }
      if (url.includes('/exam-materials?')) return new Response(saved ? '[{"id":8,"core_type":"school_print","student_id":null,"student_name":null,"title":"JSON 파일 조합","original_name":"","created_at":"","has_html":1}]' : '[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.change(within(screen.getByLabelText('시험 Context')).getByLabelText('시험'), { target: { value: '중간고사' } })
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    const jsonFile = new File(['{"items":[{"id":1}]}'], 'print.json', { type: 'application/json' })
    fireEvent.change(within(formElement).getByLabelText('자료 제목'), { target: { value: 'JSON 파일 조합' } })
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>HTML + JSON file</p>' } })
    fireEvent.change(within(formElement).getByLabelText('JSON 파일 업로드'), { target: { files: [jsonFile] } })
    expect((within(formElement).getByLabelText('또는 JSON 붙여넣기') as HTMLTextAreaElement).value).toBe('')
    fireEvent.click(within(formElement).getByRole('button', { name: '저장' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, init]) => String(u).endsWith('/exam-materials') && init?.method === 'POST')).toBe(true))
    const postCall = fetchMock.mock.calls.find(([u, init]) => String(u).endsWith('/exam-materials') && init?.method === 'POST')
    const payload = postCall?.[1]?.body as FormData
    expect(payload.get('html_text')).toBe('<p>HTML + JSON file</p>')
    expect(payload.get('json_file')).toBe(jsonFile)
    expect(payload.get('json_text')).toBe('')
    expect(payload.get('school')).toBe('신사중')
    expect(payload.get('grade')).toBe('3학년')
    expect(payload.get('exam')).toBe('중간고사')
    expect(await screen.findByRole('status')).toHaveTextContent('HTML과 JSON을 한 세트로 저장했습니다.')
    expect(screen.getByRole('button', { name: /학교 프린트 추가\s*·\s*저장\s*1\s*건/ })).toBeInTheDocument()
    expect(within(screen.getByLabelText('시험 Context')).getByLabelText('시험')).toHaveValue('중간고사')
    fireEvent.click(screen.getByRole('button', { name: 'FINAL 준비' }))
    expect(await screen.findByText('JSON 파일 조합')).toBeInTheDocument()
  })

  it('HTML이 없는 제출 경로는 조용히 끝내지 않고 저장 불가 이유를 표시한다', async()=>{
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    fireEvent.submit(formElement)
    expect(await screen.findByRole('status')).toHaveTextContent('학교 프린트 HTML을 입력하거나 HTML 파일을 선택해 주세요.')
  })

  it('학교 프린트 저장이 422 detail 배열로 실패해도 문자열 오류를 표시하고 Wiki는 crash하지 않는다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-materials') && init?.method === 'POST') return new Response(JSON.stringify({ detail: [
        { type: 'missing', loc: ['query', 'school'], msg: 'Field required' },
        { type: 'missing', loc: ['query', 'grade'], msg: 'Field required' },
      ] }), { status: 422, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    fireEvent.change(within(formElement).getByLabelText('HTML 붙여넣기'), { target: { value: '<p>5과 본문 HTML</p>' } })
    fireEvent.submit(formElement)
    expect(await screen.findByRole('status')).toHaveTextContent('저장하지 못했습니다. (Field required)')
    expect(screen.getByRole('heading', { name: '시험 자료 한눈에 모으기' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '학교 프린트 추가' })).toBeInTheDocument()
  })

  it('STEP 4의 FINAL 준비는 출제자 Digital Twin을 포함한 5 CORE 선택형 워크스페이스를 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    for(const title of ['출제자 Digital Twin','제작 문제','학교 프린트','학생 오답','수업 인사이트']) {
      expect(workspace.getByRole('heading',{name:new RegExp(title)})).toBeInTheDocument()
    }
  })

  it('FINAL Workspace에는 FINAL 완성본을 저장하는 영역이 있고 결과 파일 또는 HTML 중 하나만 있으면 저장할 수 있다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    expect(workspace.getByText('아직 저장된 FINAL 완성본이 없습니다. 외부에서 만든 결과물을 아래에서 저장할 수 있습니다.')).toBeInTheDocument()
    const saveButton = workspace.getByRole('button',{name:'FINAL 완성본 저장'})
    expect(saveButton).toBeDisabled()
    fireEvent.change(workspace.getByLabelText('HTML 붙여넣기'),{target:{value:'<p>FINAL 완성본</p>'}})
    expect(saveButton).not.toBeDisabled()
  })

  it('STEP 4의 직보 준비는 FINAL 완성본이 없으면 대기 안내를 보여준다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'직보 준비'}))
    expect(await screen.findByText('아직 FINAL 자료가 없습니다. FINAL 제작 후 직보를 준비할 수 있습니다.')).toBeInTheDocument()
  })

  it('직보 준비는 FINAL 완성본이 있으면 FINAL 완성본 → 최신 수업 인사이트 → 학생 반복 오답 순으로 보여준다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '전체', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.includes('/final-deliverables?')) return new Response(JSON.stringify([{ id: 1, kind: 'final', school: '신사중', grade: '3학년', year: new Date().getFullYear(), semester: '1학기', exam: '전체', title: '기말 FINAL', original_name: '', created_at: new Date().toISOString(), has_html: 1 }]), { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button', { name: '직보 준비' }))
    const heading = await screen.findByRole('heading',{name:'직보 준비'})
    const workspace = within(heading.closest('section') as HTMLElement)
    expect(workspace.queryByText('아직 FINAL 자료가 없습니다. FINAL 제작 후 직보를 준비할 수 있습니다.')).not.toBeInTheDocument()
    const coreHeadings = workspace.getAllByRole('heading',{level:3}).map(el=>el.textContent)
    const order = ['FINAL 완성본','최신 수업 인사이트','학생 반복 오답'].map(label=>coreHeadings.findIndex(text=>text?.includes(label)))
    expect(order.every(index=>index>=0)).toBe(true)
    expect(order).toEqual([...order].sort((a,b)=>a-b))
    expect(workspace.getByText(/기말 FINAL/)).toBeInTheDocument()
  })
})

