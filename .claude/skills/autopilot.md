# Error Autopilot

에러를 자동으로 분석하고 GitHub 이슈를 생성합니다.

## 사용법
```
/autopilot                    # 최근 1시간 에러 분석
/autopilot 30                 # 최근 30분 에러 분석
/autopilot upvy-backend       # 특정 서비스만 분석
/autopilot upvy-backend 120   # 특정 서비스, 최근 2시간
```

## 워크플로우

이 skill이 호출되면 다음을 **순서대로 자동 실행**하세요:

### 1단계: 에러 수집
- `fetch_errors` 도구로 에러 로그 조회
- 인자가 있으면 해당 서비스/시간으로 필터링
- 에러가 없으면 "최근 에러가 없습니다" 출력하고 종료

### 2단계: 에러별 분석 (각 에러에 대해)
1. **트레이스 조회**: 에러에 trace_id가 있으면 `get_trace`로 전체 요청 흐름 확인
2. **코드베이스 분석** (GitHub API 사용):
   - 에러 메시지, 스택트레이스에서 파일명/클래스명 추출
   - `gh api repos/{owner}/{repo}/contents/{path}` 로 해당 코드 조회
   - 또는 Read 도구로 로컬 경로의 코드를 분석 (로컬 개발 환경인 경우)
   - 근본 원인 파악 및 관련 코드 패턴 검색
3. **수정 방안 도출**: 구체적인 코드 수정 제안

### 3단계: GitHub 이슈 생성
각 에러(또는 관련 에러 그룹)에 대해:

```bash
gh issue create \
  --repo [서비스에 해당하는 repo] \
  --title "[ERROR] 서비스명: 에러 제목" \
  --label "bug,auto-generated" \
  --body "이슈 내용"
```

이슈 본문 형식:
```markdown
## 🔴 에러 요약
- **발생 시간**:
- **서비스**:
- **심각도**:

## 📋 에러 상세
[에러 메시지 및 스택트레이스]

## 🔍 근본 원인 분석
[코드 분석 결과]

## 📁 영향받는 파일
- `파일경로:라인번호`

## 💡 제안된 수정
[구체적인 코드 수정 방법]

## 🔗 관련 정보
- Trace ID: `xxx`
- Grafana 링크: [View in Grafana](url)

---
_이 이슈는 Error Autopilot에 의해 자동 생성되었습니다._
```

### 4단계: 결과 리포트
- 분석한 에러 개수
- 생성한 이슈 목록 (링크 포함)
- 발견된 패턴이나 반복 에러 언급

## 서비스-리포지토리 매핑

에러가 발생한 서비스에 해당하는 코드베이스 정보:

| 서비스명 | GitHub 리포 | 코드베이스 유형 |
|---------|-------------|----------------|
| upvy-backend | 12OneTwo12/upvy | Kotlin 모노레포 (backend) |
| upvy-frontend | 12OneTwo12/upvy | Next.js 모노레포 (frontend) |
| upvy-ai-crawler | 12OneTwo12/upvy | Python 모노레포 (ai-crawler) |

**분석 시**:
1. 에러 로그에서 서비스명 확인
2. 위 매핑에서 해당 GitHub 리포 찾기
3. GitHub API로 코드 분석:
   ```bash
   # 파일 내용 조회
   gh api repos/12OneTwo12/upvy/contents/upvy-backend/src/main/kotlin/... --jq '.content' | base64 -d

   # 파일 목록 조회
   gh api repos/12OneTwo12/upvy/contents/upvy-backend/src
   ```
4. 이슈 생성 시 해당 GitHub 리포 사용
5. 매핑에 없는 서비스면 사용자에게 리포 질문

## 중요 사항

- 이미 이슈가 있는 에러는 **중복 생성하지 않음** (gh issue list로 확인)
- 동일한 에러가 여러 번 발생하면 **하나의 이슈로 그룹화**
- 이슈 생성 전 사용자에게 **확인 요청** (--dry-run 옵션 시 미리보기만)
