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

describe('학생 Wiki 카드 라우팅', () => {
  const openStudentHub=()=>{render(<App/>);fireEvent.click(screen.getByRole('button',{name:/학생 오답 · 기록/}))}
  it('오답 기록·추출 카드를 클릭하면 실제 추출 컴포넌트를 렌더링한다', async()=>{
    openStudentHub();fireEvent.click(screen.getByRole('button',{name:/오답 기록·추출/}))
    expect(await screen.findByRole('region',{name:'오답 기록·추출'})).toBeInTheDocument()
    expect(screen.getByRole('heading',{name:'오답 추출'})).toBeInTheDocument()
  })
  it('학생별 오답노트 카드를 클릭하면 실제 누적 오답 컴포넌트를 렌더링한다', async()=>{
    openStudentHub();fireEvent.click(screen.getByRole('button',{name:/학생별 오답노트/}))
    expect(await screen.findByRole('region',{name:'학생별 오답노트'})).toBeInTheDocument()
    expect(screen.getByRole('heading',{name:/저장된 오답 자료/})).toBeInTheDocument()
  })
})
