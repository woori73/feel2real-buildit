import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
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

  it('STEP 1의 학생 오답 추가는 중복 메타 입력 없이 원본+JSON 세트 폼을 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학생 오답 추가/}))
    expect(screen.getByRole('heading',{name:'학생 오답 추가'})).toBeInTheDocument()
    expect(screen.getByLabelText('원본 업로드')).toBeInTheDocument()
    expect(screen.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(screen.queryByLabelText(/문제 유형|문제번호|시험명/)).not.toBeInTheDocument()
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
