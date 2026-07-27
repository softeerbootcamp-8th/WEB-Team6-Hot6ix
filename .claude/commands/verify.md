---
description: 변경된 영역의 테스트·lint·타입 검사·빌드를 실행하고 완료 보고 형식으로 정리한다
argument-hint: [be | fe | all (선택, 기본은 자동 감지)]
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(./gradlew:*), Bash(cd backend*), Bash(pnpm:*), Read, Grep, Glob
---

# 검증

대상 지정: **$ARGUMENTS** (비어 있으면 자동 감지)

CLAUDE.md 검증 규칙을 실행한다.

> 변경한 영역의 테스트, lint, 타입 검사, 빌드를 실행한다.

## 1. 검증 대상 결정

인자가 있으면 그대로 따른다 (`be` / `fe` / `all`).
없으면 변경된 경로로 판단한다.

    git status --short
    git diff --name-only dev...HEAD

`backend/` 변경 → 백엔드, `frontend/` 변경 → 프론트, 양쪽 → 둘 다.
문서·설정만 바뀌었으면 실행할 검증이 없다고 알리고 끝낸다.

## 2. 백엔드

    ( cd backend && ./gradlew test )

Gradle Wrapper를 쓴다. 전역 `gradle`을 쓰지 않는다.

**`cd`는 반드시 서브셸 `( ... )` 안에서 한다.** 셸 작업 디렉터리는
호출 간에 유지되므로, 서브셸 없이 `cd`하면 이후 명령이 엉뚱한 위치에서 돈다.

## 3. 프론트엔드

    pnpm --dir frontend lint
    pnpm --dir frontend build

`build`가 `tsc -b && vite build`라서 **타입 검사가 빌드에 포함**된다.
별도 타입 체크 명령은 없다.

포맷까지 볼 때는 아래를 추가한다. `format`(자동 수정)이 아니라
`format:check`를 쓴다. 검증 단계에서 파일을 임의로 고치지 않는다.

    pnpm --dir frontend format:check

패키지 매니저는 **pnpm 고정**이다. npm이나 yarn을 섞지 않는다.

## 4. 실패 처리

**실패를 숨기거나 우회하지 않는다.** 다음은 금지다.

- 실패하는 테스트 삭제·비활성화
- 검증 완화(assertion 약화, lint 규칙 끄기)
- 실패를 "일단 통과"로 보고

실패하면 출력 그대로 보여주고 원인을 분석한다. 고칠지는 사용자가 정한다.
한쪽이 실패해도 나머지 검증은 마저 실행한다. 부분 결과가 더 유용하다.

## 5. 보고

`references/workflow.md` 완료 보고 형식으로 정리한다.

```markdown
## 검증 결과

| 항목 | 결과 |
|---|---|
| backend test | ✅ 통과 (N개) / ❌ 실패 (N개) |
| frontend lint | ... |
| frontend build | ... |

## 변경 영역
- {건드린 영역과 파일}

## 계약 영향
- {API·DB·이벤트 계약 변경 여부. 없으면 "없음"}

## 미검증 사항
- {실행하지 않은 검증과 그 이유}
- {남은 위험}
```

**미검증 사항을 비워두지 않는다.** 통합 흐름, 동시성, 실시간 이벤트처럼
자동 검증으로 확인되지 않는 항목은 명시한다. 공용 계약이 바뀌었으면
프론트–백엔드 통합 흐름 검증이 별도로 필요하다는 점을 알린다.
