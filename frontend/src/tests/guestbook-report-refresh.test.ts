/**
 * 내 땅굴에서 방명록 글을 신고하면 방명록 목록 · 내 땅굴(글 수)을 다시 받는지 (PR #37 Codex 리뷰 P1, 명세 04: 가려진 글은 목록 · 글 수에서 빠짐).
 * 목 서버: 서로 다른 3명이 신고하면 목록 · 글 수에서 빠지는 것까지
 */
import { describe, expect, it } from 'vitest'

describe('방명록 신고 뒤 다시 받기', () => {
  it('내 땅굴 · 방문자 뷰 모두 신고가 끝나면 방명록 · 땅굴 캐시를 만료시킴', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const mine = fs.readFileSync('src/screens/burrow/MyBurrow.tsx', 'utf8')
    expect(mine).toMatch(/type: 'GUESTBOOK'[\s\S]*?onSettled: \(\) => \{[\s\S]*?burrowKeys\.guestbook\(b\.burrowId\)[\s\S]*?burrowKeys\.mine/)
    const visit = fs.readFileSync('src/screens/burrow/VisitBurrow.tsx', 'utf8')
    expect(visit).toMatch(/type: 'GUESTBOOK'[\s\S]*?onSettled: \(\) => \{[\s\S]*?burrowKeys\.guestbook\(b\.burrowId\)[\s\S]*?exploreKeys\.burrow\(owner\.id\)/)
  })

  it('완료 토스트는 신고가 접수된 뒤에만, 이미 신고했으면 "이미 신고했어요" (00 공통 16, 08 신고 팝업 1)', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const modal = fs.readFileSync('src/screens/burrow/GuestbookModal.tsx', 'utf8')
    // 예전: 신고를 보내자마자 완료 토스트 → 실패(409 등)해도 "신고가 접수되었어요"가 떴음
    expect(modal).not.toMatch(/setReportId\(null\); setToast\(TOAST\.reported\)/)
    expect(modal).toMatch(/onReported: \(\) => setToast\(TOAST\.reported\), onAlready: \(\) => setToast\(TOAST\.alreadyReported\)/)
    // 두 화면 모두 글 · 댓글 신고와 같은 훅(이미 신고함을 onAlready 로 알림)을 씀
    for (const p of ['src/screens/burrow/MyBurrow.tsx', 'src/screens/burrow/VisitBurrow.tsx']) {
      const src = fs.readFileSync(p, 'utf8')
      expect(src).toMatch(/useReport\(\)/)
      expect(src).toMatch(/onAlready: done\.onAlready[\s\S]*?onSuccess: done\.onReported/)
    }
  })

  it('방문자가 자기 글을 지울 때도 방명록 삭제 확인을 거침 (명세 05 방명록 3, PR #37 Codex 리뷰 P1)', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const src = fs.readFileSync('src/screens/burrow/GuestbookModal.tsx', 'utf8')
    expect(src).not.toMatch(/onClick=\{\(\) => \{ onDeleteOwn\(/)
    expect(src).toMatch(/if \(!onDelete\) \{ onDeleteOwn\?\.\(id\)/)
  })

  it('내 정보 수정에서 바꾼 것이 있으면 꾸미기로 넘어가기 전에 저장 전 확인 (00 공통 18, PR #37 Codex 리뷰 P1)', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const mine = fs.readFileSync('src/screens/burrow/MyBurrow.tsx', 'utf8')
    expect(mine).toMatch(/const startDecor = \(\) => \{ if \(mode === 'edit' && editDirty\) setAskDecor\(true\); else goDecor\(\) \}/)
    expect(mine).toMatch(/onDirtyChange=\{setEditDirty\}/)
    expect(mine).toMatch(/<UnsavedDialog open=\{askDecor\}/)
  })

  it('내 정보 수정에서 바꾼 것이 있는데 다른 화면으로 가면(상단 메뉴 등) 저장 전 확인 (00 공통 18, PR #37 Codex 리뷰 P1)', async () => {
    const FS = 'node:fs'
    const fs = (await import(/* @vite-ignore */ FS)) as { readFileSync(p: string, e: string): string }
    const edit = fs.readFileSync('src/screens/burrow/MyInfoEdit.tsx', 'utf8')
    expect(edit).toMatch(/useBlocker\(\(\{ currentLocation, nextLocation \}\) => dirty && currentLocation\.pathname !== nextLocation\.pathname\)/)
    expect(edit).toMatch(/<UnsavedDialog open=\{blocker\.state === 'blocked'\}/)
  })
})

