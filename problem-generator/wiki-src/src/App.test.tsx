import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import App from './App'

beforeEach(() => {
  const storage=()=>{const values=new Map<string,string>();return{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key),clear:()=>values.clear(),key:(index:number)=>[...values.keys()][index]??null,get length(){return values.size}}}
  vi.stubGlobal('localStorage',storage());vi.stubGlobal('sessionStorage',storage())
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url=String(input)
    if(url.endsWith('/students')) return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
    if(url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}',{status:200,headers:{'Content-Type':'application/json'}})
    return new Response('[]',{status:200,headers:{'Content-Type':'application/json'}})
  }))
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

  it('STEP 1의 학교 프린트 추가는 자료 제목·원본·JSON(선택) 필드만 열고 원본만으로 저장할 수 있다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학교 프린트 추가/}))
    const heading = screen.getByRole('heading',{name:'학교 프린트 추가'})
    const form = within(heading.closest('form') as HTMLElement)
    expect(form.getByLabelText('자료 제목')).toBeInTheDocument()
    expect(form.getByLabelText('원본 파일 업로드')).toBeInTheDocument()
    expect(form.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(form.queryByLabelText(/^학생/)).not.toBeInTheDocument()
    const saveButton = form.getByRole('button', { name: '저장' })
    expect(saveButton).toBeDisabled()
    fireEvent.change(form.getByLabelText('원본 파일 업로드'), { target: { files: [new File(['x'], 'print.pdf', { type: 'application/pdf' })] } })
    expect(saveButton).not.toBeDisabled()
  })

  it('STEP 1에는 구형 도구(OCR·재구성, 오답 사진 추출, 학생별 오답노트) 안내가 없다', async()=>{
    render(<App/>)
    await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})
    expect(screen.queryByText(/OCR·재구성 도구/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/오답 사진 추출/})).not.toBeInTheDocument()
    expect(screen.queryByRole('button',{name:/학생별 오답노트/})).not.toBeInTheDocument()
  })

  it('학교 프린트 저장 시 상위 시험 Context를 그대로 전달하고 다시 입력받지 않는다', async()=>{
    const schools = [{ school: '신사중', grades: [{ grade: '3학년', exams: [{ exam: '중간', twinId: null, version: null, updatedAt: '', hasDigitalTwin: false }] }] }]
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/schools')) return new Response(JSON.stringify(schools), { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/students')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
      if (url.endsWith('/exam-draft')) return new Response('{"question_ids":[],"title":"","academy_name":"","teacher_name":""}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await screen.findByText('신사중 · 3학년')
    fireEvent.click(screen.getByRole('button', { name: /학교 프린트 추가/ }))
    const formElement = screen.getByRole('heading', { name: '학교 프린트 추가' }).closest('form') as HTMLFormElement
    fireEvent.change(within(formElement).getByLabelText('원본 파일 업로드'), { target: { files: [new File(['x'], 'print.pdf', { type: 'application/pdf' })] } })
    fireEvent.submit(formElement)
    await waitFor(() => expect(fetchMock.mock.calls.some(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')).toBe(true))
    const call = fetchMock.mock.calls.find(([u, init]) => String(u).includes('exam-materials') && init?.method === 'POST')
    const form = call?.[1]?.body as FormData
    expect(form.get('school')).toBe('신사중')
    expect(form.get('grade')).toBe('3학년')
    expect(form.get('exam')).toBe('중간')
  })

  it('STEP 4의 FINAL 준비는 4 CORE 선택형 워크스페이스를 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'FINAL 준비'}))
    const heading = await screen.findByRole('heading',{name:'현재 시험 자료'})
    const workspace = within(heading.closest('section') as HTMLElement)
    for(const title of ['제작 문제','학교 프린트','학생 오답','수업 인사이트']) {
      expect(workspace.getByRole('heading',{name:new RegExp(title)})).toBeInTheDocument()
    }
  })

  it('STEP 4의 직보 준비는 수업 인사이트 중심의 압축 뷰를 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:'직보 준비'}))
    expect(await screen.findByRole('heading',{name:'직보 핵심 신호'})).toBeInTheDocument()
  })
})
