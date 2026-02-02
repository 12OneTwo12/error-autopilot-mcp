# Error Autopilot MCP (Kotlin)

Loki/Tempo에서 에러를 자동으로 수집하고, **GitHub API를 통해 코드를 분석**한 후 GitHub 이슈를 생성하는 Claude Code MCP 서버입니다.

**Kotlin 버전** - 공식 [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk) 사용

## Features

- 🔍 **Loki 에러 모니터링**: 실시간 에러 로그 수집 및 필터링
- 🔗 **Tempo 분산 트레이싱**: trace_id로 전체 요청 흐름 추적
- 📝 **GitHub 이슈 자동 생성**: 분석된 에러를 이슈로 변환
- 🌐 **GitHub API 코드 분석**: 로컬 파일 없이 원격 저장소 코드 분석

## Quick Start

```bash
# 1. 클론 및 빌드
git clone https://github.com/12OneTwo12/error-autopilot-mcp-kotlin.git
cd error-autopilot-mcp-kotlin
./gradlew build

# 2. Fat JAR 생성
./gradlew jar

# 3. Claude Code에 등록
```

## Claude Code 설정

`~/.claude.json`에 MCP 서버 추가:

```json
{
  "mcpServers": {
    "error-autopilot": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/error-autopilot-mcp-kotlin/build/libs/error-autopilot-mcp-1.0.0.jar"],
      "env": {
        "LOKI_URL": "https://your-loki-server",
        "TEMPO_URL": "https://your-tempo-server"
      }
    }
  }
}
```

## 환경변수

| 변수 | 설명 | 필수 |
|------|------|------|
| `LOKI_URL` | Loki 서버 URL | ✅ |
| `LOKI_ORG_ID` | Loki 조직 ID | - |
| `TEMPO_URL` | Tempo 서버 URL | - |
| `TEMPO_ORG_ID` | Tempo 조직 ID | - |

## MCP 도구

| 도구 | 설명 |
|------|------|
| `fetch_errors` | Loki에서 에러 로그 조회 |
| `query_logs` | 커스텀 LogQL 쿼리 실행 |
| `get_trace` | trace_id로 분산 트레이스 조회 |
| `search_traces` | 트레이스 검색 |
| `list_services` | 서비스 목록 조회 |
| `list_labels` | Loki 레이블 목록 조회 |
| `get_error_summary` | 에러 요약 |
| `test_connection` | Loki 연결 테스트 |
| `test_tempo_connection` | Tempo 연결 테스트 |

## 빌드

```bash
# 개발 빌드
./gradlew build

# Fat JAR 생성 (모든 의존성 포함)
./gradlew jar

# 실행
java -jar build/libs/error-autopilot-mcp-1.0.0.jar
```

## 프로젝트 구조

```
error-autopilot-mcp-kotlin/
├── src/main/kotlin/io/github/onetwo/errorautopilot/
│   ├── Main.kt              # MCP 서버 진입점 및 도구 정의
│   ├── adapter/
│   │   ├── LokiAdapter.kt   # Loki API 클라이언트
│   │   └── TempoAdapter.kt  # Tempo API 클라이언트
│   └── model/
│       └── Models.kt        # 데이터 클래스
├── src/main/resources/
│   └── logback.xml          # 로깅 설정
├── build.gradle.kts
└── settings.gradle.kts
```

## License

MIT
