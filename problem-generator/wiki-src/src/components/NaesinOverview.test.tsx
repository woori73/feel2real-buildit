import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import NaesinWikiV1 from './NaesinWikiV1'
beforeEach(() => {
  const values = new Map<string,string>()
  const storage = { getItem: (k:string) => values.get(k) ?? null, setItem: (k:string,v:string) => values.set(k,v), removeItem: (k:string) => values.delete(k) }
  vi.stubGlobal('localStorage', storage); vi.stubGlobal('sessionStorage', storage)
  vi.stubGlobal('fetch', vi.fn(async () => new Response('[]', {status:200,headers:{'Content-Type':'application/json'}})))
  HTMLElement.prototype.scrollIntoView = vi.fn()
})
afterEach(() => { cleanup(); vi.unstubAllGlobals() })
it('given 내신위키 홈 when 개요를 열면 then 오답 UI 없이 자료 등록과 접힌 상세 자료를 제공한다', async () => {
  const navigate = vi.fn()
  const {container} = render(<NaesinWikiV1 onNavigate={navigate}/>)
  for (const name of ['자료 모으기','수업 기록']) expect(screen.getByRole('heading',{name})).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:/오답/})).not.toBeInTheDocument()
  expect(screen.queryByRole('heading',{name:/오답/})).not.toBeInTheDocument()
  expect(container.querySelector('.wrong-review')).not.toBeInTheDocument()
  const panels = container.querySelectorAll('details.wiki-secondary-panel')
  expect(panels).toHaveLength(2)
  panels.forEach(panel => expect(panel).not.toHaveAttribute('open'))
  fireEvent.click(screen.getByRole('button',{name:'수업 인사이트 기록 0',exact:true}))
  expect(container.querySelector('.insight-details')).toHaveAttribute('open')
  expect(screen.queryByRole('heading',{name:'문제 준비'})).not.toBeInTheDocument()
  expect(screen.queryByRole('heading',{name:'시험 준비'})).not.toBeInTheDocument()
  expect(container.querySelector('.current-context-card')).not.toBeInTheDocument()
})
it('given PDF 자료 when 파일을 골라 저장하면 then HTML 입력 없이 원본을 전송한다', async () => {
  let uploaded: FormData | null = null
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if(url.endsWith('/schools')) return new Response(JSON.stringify([{school:'신구중',grades:[{grade:'3학년',exams:[{exam:'전체',hasDigitalTwin:false}]}]}]),{status:200})
    if(init?.method === 'POST' && url.endsWith('/exam-materials')) { uploaded=init.body as FormData;return new Response('{"id":1}',{status:201}) }
    return new Response('[]',{status:200})
  }))
  render(<NaesinWikiV1 onNavigate={vi.fn()}/>)
  await screen.findByRole('option',{name:'신구중'})
  fireEvent.click(screen.getByRole('button',{name:/학교 프린트 추가/}))
  const chooser=screen.getByLabelText('학습자료 파일 선택')
  expect(chooser).toHaveAttribute('accept','.html,.pdf,.hwpx,.docx')
  fireEvent.change(chooser,{target:{files:[new File(['%PDF-1.7\nmaterial'],'school.pdf',{type:'application/pdf'})]}})
  fireEvent.click(screen.getByRole('button',{name:'저장',exact:true}))
  expect(await screen.findByRole('status')).toHaveTextContent('자료와 추가 정보를 저장했습니다.')
  expect((uploaded?.get('original') as File).name).toBe('school.pdf')
})
