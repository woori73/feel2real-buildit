import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import NaesinWikiV1 from './NaesinWikiV1'

beforeEach(()=>{
  const values=new Map<string,string>()
  const storage={getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)}
  vi.stubGlobal('localStorage',storage);vi.stubGlobal('sessionStorage',storage)
  HTMLElement.prototype.scrollIntoView=vi.fn()
})
afterEach(()=>{cleanup();vi.unstubAllGlobals()})

it('given a PDF school print when saved then upload the original without HTML or JSON',async()=>{
  let uploaded:FormData|null=null
  vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{
    const url=String(input)
    if(url.endsWith('/schools'))return new Response(JSON.stringify([{school:'압구정중',grades:[{grade:'2학년',exams:[{exam:'기말고사',hasDigitalTwin:false}]}]}]),{status:200})
    if(init?.method==='POST'&&url.endsWith('/exam-materials')){uploaded=init.body as FormData;return new Response('{"id":1}',{status:201})}
    return new Response('[]',{status:200})
  }))
  render(<NaesinWikiV1 onNavigate={vi.fn()}/> )
  await screen.findByRole('option',{name:'압구정중'})
  fireEvent.click(screen.getByRole('button',{name:/학교 프린트 추가/}))
  const chooser=screen.getByLabelText('학습자료 파일 선택')
  expect(chooser.getAttribute('accept')).toContain('.pdf')
  expect(chooser.getAttribute('accept')).toContain('application/pdf')
  expect(screen.getByText(/PDF 원본만 선택해도 저장할 수 있습니다/)).toBeInTheDocument()
  const original=new File(['%PDF-1.7\nprint'],'학교프린트.pdf',{type:'application/pdf'})
  fireEvent.change(chooser,{target:{files:[original]}})
  fireEvent.click(screen.getByRole('button',{name:'저장',exact:true}))
  await screen.findByText('자료와 추가 정보를 저장했습니다.')
  expect(uploaded?.get('original')).toBe(original)
  expect(uploaded?.get('html_text')).toBe('')
  expect(uploaded?.get('json_text')).toBe('')
})
