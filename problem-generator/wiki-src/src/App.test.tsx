import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

describe('내신 Wiki V1 시험 Context 홈', () => {
  it('학교·학년·연도·학기·시험과 4 CORE만 중심에 표시한다', async()=>{
    render(<App/>)
    expect(await screen.findByRole('heading',{name:'시험 자료 한눈에 모으기'})).toBeInTheDocument()
    expect(screen.getByLabelText('시험 Context')).toBeInTheDocument()
    for(const name of ['제작 문제','학교 프린트','학생 오답','수업 인사이트']) {
      expect(screen.getByRole('button',{name:new RegExp(name)})).toBeInTheDocument()
    }
    expect(screen.getByRole('button',{name:'Admin / Final 자료 보기'})).toBeInTheDocument()
  })

  it('학생 오답 카드는 중복 메타 입력 없이 원본+JSON 세트 폼을 연다', async()=>{
    render(<App/>); fireEvent.click(await screen.findByRole('button',{name:/학생 오답/}))
    expect(screen.getByRole('heading',{name:'학생 오답 추가'})).toBeInTheDocument()
    expect(screen.getByLabelText('원본 업로드')).toBeInTheDocument()
    expect(screen.getByLabelText('JSON 파일 업로드')).toBeInTheDocument()
    expect(screen.queryByLabelText(/문제 유형|문제번호|시험명/)).not.toBeInTheDocument()
  })
})
