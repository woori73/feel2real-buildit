import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import NaesinWorkspace from './NaesinWorkspace'
vi.mock('./NaesinWikiV1', () => ({ default: () => <input aria-label="자료 제목" /> }))
vi.mock('./GrammarBank', () => ({ default: () => <input aria-label="문법 검색" /> }))
vi.mock('./McpProblemMaker', () => ({ default: ({ onNavigate }: { onNavigate: (page: string) => void }) => <button onClick={() => onNavigate('state')}>자료 올리러 가기</button> }))
afterEach(() => { cleanup(); window.history.replaceState({}, '', '/') })
describe('내신위키 통합 작업실', () => {
  it('given 작성 중인 자료 when 다른 기능을 사용하고 돌아오면 then 입력과 문법 검색을 보존한다', () => {
    render(<NaesinWorkspace onNavigate={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('자료 제목'), { target: { value: '기말 자료' } })
    fireEvent.click(screen.getByRole('button', { name: /문법문제은행/ }))
    fireEvent.change(screen.getByLabelText('문법 검색'), { target: { value: '관계대명사' } })
    fireEvent.click(screen.getByRole('button', { name: /문제·자료 업로드/ }))
    expect(screen.getByLabelText('자료 제목')).toHaveValue('기말 자료')
    fireEvent.click(screen.getByRole('button', { name: /문법문제은행/ }))
    expect(screen.getByLabelText('문법 검색')).toHaveValue('관계대명사')
  })
  it('given 예전 제작소 링크 when 페이지를 열면 then 제작소 탭 없이 자료 업로드를 연다', () => {
    window.history.replaceState({}, '', '/?section=production')
    render(<NaesinWorkspace onNavigate={vi.fn()} />)
    expect(screen.queryByRole('button', { name: /내신제작소/ })).not.toBeInTheDocument()
    expect(screen.queryByTitle('내신제작소 작업 화면')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /문제·자료 업로드/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('자료 제목')).toBeInTheDocument()
  })
  it('given MCP 탭 when 자료 업로드로 이동하면 then 같은 화면의 업로드 탭을 연다', () => {
    // given
    const onNavigate = vi.fn()
    window.history.replaceState({}, '', '/?section=mcp')
    render(<NaesinWorkspace onNavigate={onNavigate} />)
    expect(screen.getByRole('button', { name: /MCP 문제 만들기/ })).toHaveAttribute('aria-pressed', 'true')
    // when
    fireEvent.click(screen.getByRole('button', { name: '자료 올리러 가기' }))
    // then
    expect(screen.getByRole('button', { name: /문제·자료 업로드/ })).toHaveAttribute('aria-pressed', 'true')
    expect(onNavigate).not.toHaveBeenCalled()
  })
})
