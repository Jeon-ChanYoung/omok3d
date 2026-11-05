# 3D 오목 (omok3d)

19×19×19 정육면체 판에서 두는 **3차원 오목** 게임입니다. JavaFX로 만들었습니다.

## 게임 규칙

- 판은 19×19×19 정육면체이고, 두 플레이어(흑/백)가 번갈아 돌을 둡니다.
- 플레이어는 6개 방향(앞/뒤/왼쪽/오른쪽/위/아래)에서 판을 볼 수 있습니다.
- **어느 한 방향에서 봤을 때 같은 색 돌 5개가 한 줄(가로/세로/대각선)로 보이면 승리**합니다.
  - 돌끼리 실제로 붙어 있을 필요는 없습니다. 정면에서는 떨어져 보여도 위에서 봤을 때 나란하면 됩니다.
  - 상대 돌은 무시하고 내 돌만 투영해서 판정합니다.
- 반대편 시점(앞/뒤 등)은 좌우만 뒤집힌 같은 투영이라, 실제 판정은 **3개 축 투영**만 검사합니다.

## 실행 방법

### 준비물

| 도구 | 버전 | 확인 |
|---|---|---|
| JDK | 17 이상 (21 권장) | `java -version` |
| Maven | 3.9 이상 | `mvn -v` |

JavaFX는 Maven이 자동으로 받으므로 따로 설치하지 않아도 됩니다.

### 실행

```bash
git clone <저장소 주소>
cd omok3d
mvn javafx:run
```

처음 실행할 때는 라이브러리를 내려받느라 1~2분 걸립니다.

### VSCode에서 실행

1. 확장 **Extension Pack for Java**를 설치합니다 (폴더를 열면 추천 팝업이 뜹니다).
2. `F5` 또는 실행 및 디버그 → **3D 오목 실행**을 선택합니다.
   - 진입점은 `Launcher` 클래스입니다. `App`을 직접 실행하면 "JavaFX runtime components are missing" 오류가 날 수 있습니다.

### 테스트

```bash
mvn test
```

승리 판정 로직(`Board`)에 대한 단위 테스트가 들어 있습니다.

### 자주 나는 문제

| 증상 | 해결 |
|---|---|
| `'mvn' 용어가 ... 인식되지 않습니다` | Maven 설치 후 `bin` 폴더를 Path에 추가하고 **VSCode/터미널을 완전히 재시작** |
| `'java' 용어가 ... 인식되지 않습니다` | JDK 설치 시 "Add to PATH", "Set JAVA_HOME" 옵션 체크 |
| JavaFX runtime components are missing | `App`이 아니라 `Launcher`로 실행, 또는 `mvn javafx:run` 사용 |
| 터미널 한글이 깨짐 | `chcp 65001` 입력 후 다시 실행 (게임 화면에는 영향 없음) |
| 3D 화면에 잔상이 남음 | `App`의 `SubScene` 생성 줄에서 `SceneAntialiasing.BALANCED`를 `DISABLED`로 변경 |

## 조작법

| 동작 | 방법 |
|---|---|
| 시점 회전 | 3D 화면에서 마우스 드래그 |
| 6방향 시점 전환 | 상단 버튼 (앞/뒤/왼쪽/오른쪽/위/아래), "기본 시점"으로 복귀 |
| 층 이동 | 마우스 휠, ↑↓ 키, 슬라이더, ◀ 앞 / 뒤 ▶ 버튼 |
| 돌 놓기 | 3D 화면의 보라색 층을 클릭, 또는 오른쪽 평면도를 클릭 |
| 확대/축소 | Ctrl + 휠 |
| 새 게임 | 상단 "다시 시작" |

오른쪽 아래 **투영 미니맵**은 앞·뒤 / 위·아래 / 좌·우에서 본 면을 보여줍니다. 승리하면 5목을 이룬 칸이 강조되고, 해당 돌에 노란 빛이 들어옵니다.

## 프로젝트 구조

```
omok3d/
├─ pom.xml                         빌드 설정 (JavaFX 21, JUnit 5)
├─ src/
│  ├─ main/
│  │  ├─ java/omok3d/
│  │  │  ├─ Board.java             19³ 보드, 착수, 투영 기반 승리 판정 (UI와 무관한 순수 로직)
│  │  │  ├─ App.java               JavaFX UI (3D 뷰, 평면도, 미니맵, 입력 처리)
│  │  │  └─ Launcher.java          IDE/jar 실행용 진입점
│  │  └─ resources/
│  │     └─ style.css              UI 스타일 (파스텔 테마)
│  └─ test/java/omok3d/
│     └─ BoardTest.java            승리 판정 단위 테스트
├─ .github/                        CI(GitHub Actions), PR 템플릿
└─ .vscode/                        VSCode 공용 설정
```

설계 원칙: **게임 규칙은 `Board`, 화면은 `App`**으로 분리합니다. 규칙을 바꿀 때는 `Board`와 `BoardTest`만 건드리면 됩니다.

## 협업 가이드

### 브랜치 전략

- `main`: 항상 실행 가능한 상태 유지. 직접 push하지 않고 PR로만 병합합니다.
- 작업 브랜치: `feature/기능명`, `fix/버그명`, `docs/내용` (예: `feature/ai-player`)

```bash
git switch main && git pull
git switch -c feature/ai-player
# ... 작업 ...
git add -A && git commit -m "feat: 간단한 AI 상대 추가"
git push -u origin feature/ai-player
# GitHub에서 Pull Request 생성
```

### 커밋 메시지

`종류: 한 줄 설명` 형식을 권장합니다.

| 종류 | 용도 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작은 그대로, 구조 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 |
| `style` | UI 스타일, 포맷 |

### PR 규칙

- PR을 올리기 전에 `mvn test`가 통과하는지 확인합니다. GitHub Actions(CI)가 PR마다 자동으로 빌드와 테스트를 돌립니다.
- UI를 바꿨다면 스크린샷을 첨부합니다.
- 최소 1명이 리뷰한 뒤 병합합니다.
- 충돌을 줄이려면 `App.java`에서 같은 부분을 여러 명이 동시에 고치지 않도록 미리 이야기합니다.

### 저장소 처음 올릴 때 (저장소 만든 사람만)

```bash
cd omok3d
git init -b main
git add -A
git commit -m "chore: 프로젝트 초기 세팅"
git remote add origin https://github.com/<계정>/<저장소>.git
git push -u origin main
```

GitHub 저장소 설정(Settings → Branches)에서 `main` 브랜치 보호(PR 필수, CI 통과 필수)를 켜두면 안전합니다.

## 로드맵

- [ ] AI 상대 (싱글 플레이)
- [ ] 6목 이상 금지/흑 금수 등 선공 우위 보정 규칙 검토 (플레이테스트 후 결정)
- [ ] 무르기, 기보 저장/불러오기
- [ ] `App.java` 분리 (3D 뷰 / 평면도 / 미니맵 / 컨트롤러)
- [ ] 온라인 대전
- [ ] 실행 파일 배포 (`jpackage` 또는 `jlink`)
