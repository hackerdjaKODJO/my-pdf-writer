# 아키텍처

> My PDF Writer v0.1.0의 설계 문서
> 단일 모듈 Compose 앱 · Kotlin · PDFBox-Android · `PdfRenderer`

---

## 1. 개요

앱은 크게 네 계층으로 나뉩니다.

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)                                                 │
│  LibraryScreen · ViewerScreen · ViewerSheets · PdfCanvas     │
└───────────────┬──────────────────────────────────────────────┘
                │ StateFlow / 이벤트
┌───────────────▼──────────────────────────────────────────────┐
│ PdfViewModel                                                 │
│  문서 열기 · 도구 · 주석 · 페이지 구성 · 메모 · 검색 · 내보내기 │
└───────┬───────────────────────────────┬──────────────────────┘
        │                               │
┌───────▼──────────────┐      ┌─────────▼──────────────────────┐
│ data                 │      │ pdf                            │
│  Models · Stores     │      │  PdfRepository                 │
│  (JSON/SharedPrefs)  │      │  AnnotationPainter · PdfExporter│
└──────────────────────┘      └─────────┬──────────────────────┘
                                        │
                              ┌─────────▼──────────┐
                              │ Android 프레임워크  │
                              │ PdfRenderer, Canvas│
                              │ SAF, File I/O      │
                              └────────────────────┘
```

- **UI**: 상태를 관찰하고 사용자 입력을 ViewModel에 전달합니다.
- **PdfViewModel**: 문서 단위 상태(`ViewerUiState`)와 페이지/주석 작업을 조율합니다.
- **data**: 직렬화 모델과 영속화 저장소입니다.
- **pdf**: PDF 조립·렌더링·텍스트 추출·내보내기를 담당합니다.

---

## 2. 패키지 구조

```
com.hackerdjakodjo.mypdfwriter
├── MainActivity.kt          # SAF 런처 3종, ACTION_VIEW, 홈/뷰어 전환
├── MyPdfWriterApp.kt        # PDFBoxResourceLoader 초기화
├── data
│   ├── Models.kt            # NormalizedPoint, Annotation, PageSpec, DocState, RecentDoc
│   ├── PageTransforms.kt    # 회전 시 좌표/굵기/글자 크기 보정 (순수 함수)
│   └── Stores.kt            # DocStateStore, LibraryStore
├── pdf
│   ├── PdfRepository.kt     # 복사, 조립, 렌더 캐시, 텍스트 추출, 페이지 크기
│   ├── AnnotationPainter.kt # 화면/내보내기 공용 페인터
│   └── PdfExporter.kt       # 주석 평탄화 + 회전 행렬
└── ui
    ├── LibraryScreen.kt
    ├── ViewerScreen.kt
    ├── ViewerSheets.kt
    ├── PdfCanvas.kt
    ├── PdfViewModel.kt
    └── theme/Theme.kt
```

의도적으로 **Navigation, Room, Hilt를 사용하지 않습니다.**

- 화면이 홈/뷰어 두 개뿐이라 Navigation 그래프가 이득이 없습니다.
- 저장할 데이터가 문서 단위 JSON 한 개라 Room의 스키마/마이그레이션 비용이 더 큽니다.
- 의존성 그래프가 단순해 Hilt 없이 `AndroidViewModel`에서 직접 생성합니다.

---

## 3. 데이터 모델

### 3-1. `PageSpec` — 작업 문서의 한 페이지

```kotlin
data class PageSpec(
    val sourcePage: Int?,      // 원본 PDF 페이지 인덱스, null이면 빈 페이지
    val extraRotation: Int = 0 // 원본 /Rotate에 더할 시계방향 회전 (0/90/180/270)
)
```

페이지 **순서·삭제·빈 페이지·추가 회전**을 모두 `List<PageSpec>` 하나로 표현합니다.
원본 PDF는 절대 수정하지 않습니다.

### 3-2. `Annotation` — 필기 한 개

```kotlin
data class Annotation(
    val id: String,                 // UUID
    val type: AnnotationType,       // PEN, HIGHLIGHT, TEXT, RECT, ARROW
    val page: Int,                  // 현재 작업 문서의 페이지 인덱스
    val color: Int,                 // ARGB
    val width: Float,               // 페이지 너비 대비 선 굵기 (0~1)
    val points: List<NormalizedPoint>, // 0~1 정규화 좌표
    val text: String = "",          // TEXT 전용
    val fontSize: Float = 0.024f,   // 페이지 높이 대비 글자 크기
    val textRotation: Int = 0,      // 페이지 회전에 맞춘 글자 회전 (0/90/180/270)
)
```

- 좌표는 **페이지 표시 영역 기준 0~1**입니다. 화면 크기·줌·해상도와 무관합니다.
- `width`는 페이지 **너비** 기준, `fontSize`는 페이지 **높이** 기준입니다.
  회전 시 이 기준이 달라지므로 보정이 필요합니다(6장).
- `textRotation`은 텍스트가 페이지와 함께 돌아가도록 저장합니다.

### 3-3. `DocState` — 문서 단위 영속 상태

```kotlin
data class DocState(
    val docId: String,               // SHA-256(uri)
    val displayName: String,
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val notes: Map<Int, String>,     // 페이지 인덱스 → 메모
)
```

`normalized(sourcePageCount)`는 원본 페이지 수 변경/손상 시 다음을 수행합니다.

- 유효하지 않은 `PageSpec`(원본 인덱스 초과)을 제거합니다.
- 남은 페이지의 **이전 인덱스 → 새 인덱스 맵**을 만들어 주석과 메모를 재매핑합니다.
  (페이지가 하나 삭제됐다고 뒤 페이지의 필기가 한 칸씩 밀리면 안 됩니다.)
- 살아남은 페이지가 없으면 원본 페이지 목록으로 초기화하고 주석/메모를 비웁니다.

### 3-4. `RecentDoc` — 최근 문서

```kotlin
data class RecentDoc(
    val uri: String,
    val name: String,
    val pageCount: Int,
    val lastOpenedAt: Long,
)
```

---

## 4. 문서 열기와 페이지 조립

### 4-1. 열기 흐름

```
SAF URI
  │ takePersistableUriPermission (가능한 경우)
  │ queryDisplayName
  ▼
docId = SHA-256(uri.toString())
  │
  ├─ files/sources/<docId>.pdf      ← 원본 복사
  ├─ files/docstates/<docId>.json   ← 이전 작업 상태 로드(있으면)
  ▼
repo.assemble(source, pages, cache/working/<docId>.pdf)
  │
  ▼
renderPage(0)
```

- 원본을 앱 저장소로 복사하는 이유: SAF URI 권한이 사라져도 작업을 이어가기 위해서입니다.
- `docId`가 URI 기반이라 같은 파일을 다시 열면 이전 필기가 복원됩니다.
- `ACTION_VIEW`로 외부에서 열면 최근 목록에는 추가하지 않습니다(`addToRecents = false`).

### 4-2. `assemble()` — 작업용 PDF 만들기

```kotlin
PDDocument.load(source).use { src ->
    PDDocument().use { dst ->
        specs.forEach { spec ->
            if (spec.sourcePage == null) {
                dst.addPage(PDPage(blankBox))
            } else {
                val imported = dst.importPage(src.getPage(spec.sourcePage))
                imported.rotation = normalize(original.rotation + spec.extraRotation)
            }
        }
        dst.save(target)
    }
}
```

- 빈 페이지 크기: 첫 원본 페이지의 `cropBox`, 없으면 A4.
- 페이지 작업이 일어날 때마다 **원본에서 전체를 다시 조립**합니다.
  - 장점: 상태가 단순하고, 삭제/이동/회전이 서로 꼬이지 않습니다.
  - 단점: 페이지 수가 매우 많은 문서에서는 조립 비용이 큽니다.
    v0.2에서 증분 조립 또는 백그라운드 우선순위 조정을 검토합니다.
- 조립 후 `repo.newVersion()`으로 렌더 캐시를 무효화합니다.

### 4-3. 화면 렌더링

- `PdfRenderer`로 작업용 PDF의 현재 페이지만 렌더링합니다.
- 배율: `RENDER_SCALE = 2.2f` (PDF 포인트 → 픽셀)
- 비트맵 폭: `320..2400` px로 제한, 높이는 비율 유지
- 캐시: `LruCache<String, RenderedPage>`, 최대 `maxMemory / 6`(최소 24 MB)
- 캐시 키: `version:lastModified:pageIndex:scale`
- 렌더링은 `Mutex`로 직렬화합니다. `PdfRenderer`는 동시 접근에 안전하지 않기 때문입니다.

---

## 5. 필기 파이프라인

### 5-1. 제스처 → 정규화 좌표

`PdfCanvas`가 페이지 비트맵의 표시 사각형을 기준으로 터치 좌표를 변환합니다.

```
normalized.x = (touch.x - pageLeft) / pageWidth
normalized.y = (touch.y - pageTop) / pageHeight
```

- `VIEW` 도구: 한 손가락 이동, 두 손가락 핀치 줌(1x~6x)
- 그리기 도구: 한 손가락으로 그리기, 두 손가락은 줌/이동
- `TEXT`: 탭한 위치에 텍스트 입력 다이얼로그
- `ERASER`: 지나간 좌표 목록으로 히트 테스트 후 주석 제거

### 5-2. 주석 추가

```kotlin
fun addStroke(points: List<NormalizedPoint>) {
    val type = when (tool) { PEN, HIGHLIGHT, RECT, ARROW ... }
    val usable = if (type == RECT || type == ARROW) {
        listOf(points.first(), points.last())   // 시작점/끝점만 저장
    } else points
    mutateAnnotations { it + Annotation(type, pageIndex, color, width, usable) }
}
```

- `mutateAnnotations`가 이전 목록을 undo 스택에 넣고, redo 스택을 비우고, 즉시 저장합니다.
- undo 스택은 최대 60개입니다.
- 페이지 작업(회전/이동/삭제/빈 페이지)은 undo 스택을 초기화합니다.
  (주석 목록만 되돌리면 페이지 구성과 어긋나기 때문입니다.)

### 5-3. 공용 페인터

`AnnotationPainter`는 화면 오버레이와 내보내기에서 **동일하게** 사용됩니다.

| 타입 | 그리기 |
| --- | --- |
| PEN | 점들을 2차 베지어(quadTo)로 부드럽게 연결, 둥근 캡/조인 |
| HIGHLIGHT | 같은 경로를 alpha 96으로 굵게 |
| RECT | 두 점의 min/max로 사각형 |
| ARROW | 시작점→끝점 선 + 끝점에 삼각형 머리(길이 제한) |
| TEXT | 앵커에 `translate` 후 `rotate(textRotation)`하고 줄 단위 `drawText` |

한국어는 `sans-serif` 시스템 폰트로 그립니다. 내보낼 때도 같은 Canvas를 쓰므로
PDF 뷰어의 폰트 유무와 무관하게 모양이 유지됩니다.

---

## 6. 회전 보정

페이지를 90° 회전하면 표시 영역의 가로/세로가 바뀝니다.
정규화 좌표를 그대로 두면 필기가 엉뚱한 곳으로 가고, 선 굵기/글자 크기도 비율이 깨집니다.

### 6-1. 좌표 변환

시계방향 `delta`에 대해:

| delta | 점 변환 |
| --- | --- |
| 90° | `(x, y) → (1 - y, x)` |
| 180° | `(x, y) → (1 - x, 1 - y)` |
| 270° | `(x, y) → (y, 1 - x)` |

### 6-2. 굵기·글자 크기 보정

회전 전 페이지 표시 비율 `aspect = displayWidth / displayHeight`라 할 때:

- `delta == 180`: `width`, `fontSize` 유지
- `delta == 90/270`: `width *= aspect`, `fontSize /= aspect`

선 굵기는 페이지 **너비** 기준, 글자 크기는 페이지 **높이** 기준이므로
가로/세로가 뒤바뀔 때 서로 반대 방향으로 보정합니다.

### 6-3. 텍스트 방향

`textRotation = normalize(textRotation + delta)`로 글자 자체를 페이지와 함께 회전합니다.
`AnnotationPainter.drawText`가 앵커에서 `canvas.rotate(textRotation)` 후 그립니다.

### 6-4. 원본 구성으로 초기화

`resetPages()`는 페이지 구성을 원본 순서로 되돌리면서, 각 주석을 **원래 페이지/방향**으로 복원합니다.

```kotlin
val delta = normalize(-spec.extraRotation)
val size = sourcePageSizes[spec.sourcePage]
val baseAspect = size.width / size.height
val aspect = if (spec.extraRotation % 180 == 0) baseAspect else 1f / baseAspect
rotateAnnotation(annotation, delta, aspect).copy(page = spec.sourcePage)
```

- `sourcePageSizes()`는 원본 페이지의 `/Rotate`를 반영한 표시 크기를 돌려줍니다.
- 빈 페이지에 있던 주석은 원본 페이지가 없으므로 제거됩니다.
- 메모도 `sourcePage` 기준으로 재매핑합니다.

---

## 7. 페이지 작업과 인덱스 재매핑

| 작업 | `PageSpec` | 주석/메모 |
| --- | --- | --- |
| 회전 | 해당 spec의 `extraRotation += delta` | 해당 페이지 주석 회전 보정 |
| 위/아래 이동 | 두 spec 교환 | 두 페이지의 `page` 키 교환 |
| 삭제 | 해당 spec 제거 | 해당 페이지 주석/메모 삭제, 뒤 페이지 -1 |
| 빈 페이지 추가 | `insertAt`에 `PageSpec(null)` 삽입 | `page >= insertAt`이면 +1 |
| 초기화 | `List(sourcePageCount) { PageSpec(it) }` | 6-4의 역회전 + 원본 페이지로 재매핑 |

모든 작업은 `rebuild(state)`를 거쳐 작업용 PDF를 다시 조립하고, 현재 페이지를 안전한 인덱스로 보정합니다.

---

## 8. 저장

### 8-1. 문서 상태

- 위치: `files/docstates/<docId>.json`
- 형식: `org.json` (version, name, pages, annotations, notes)
- 쓰기: 임시 파일(`.tmp`)에 쓴 뒤 rename, 실패 시 copy 후 삭제
- 시점: 주석 변경, 메모 저장, 페이지 작업, 문서 닫기, 문서 열기 직후

JSON이 작아(필기 수천 개 수준) 전체 쓰기로도 충분합니다.
v0.2에서 디바운스/백그라운드 저장을 검토할 수 있습니다.

### 8-2. 최근 문서

- 위치: `SharedPreferences("library")`
- 키: `recents_v1`
- 최대 40개, 최근 순
- `removeRecent(uri, deleteAnnotations)`:
  - 목록에서 제거
  - 체크 시 `docstates/<docId>.json`과 `sources/<docId>.pdf`도 삭제
  - **원본 PDF(사용자 파일)는 삭제하지 않음**

---

## 9. 검색

```
query 입력 (220ms 디바운스)
  │
  ├─ 페이지 텍스트 캐시 확인
  │    key = docId:lastModified:pageCount
  │    없으면 PDFTextStripper로 페이지별 추출
  ▼
페이지별 대소문자 무시 매칭
  - 공백 정규화
  - 최대 300페이지 히트
  - 스니펫: 첫 매치 앞 40자 + 뒤 70자
  ▼
SearchHit(page, count, snippet)
```

- 스캔 PDF(텍스트 레이어 없음)는 결과가 없고, UI에서 안내합니다.
- 검색 결과를 탭하면 해당 페이지로 이동합니다.
- 페이지 작업 후 `pageTexts` 캐시를 무효화합니다.

---

## 10. 내보내기

### 10-1. 흐름

```
SAF CreateDocument("..._주석.pdf")
  │ openOutputStream(uri, "wt")   ← 기존 내용 truncate
  ▼
PdfExporter.export(source, specs, annotations, out)
  ├─ 원본에서 페이지 import + rotation 적용
  ├─ 주석이 있는 페이지만:
  │    Canvas(bitmap 2x)에 AnnotationPainter.draw
  │    LosslessFactory.createFromImage
  │    PDPageContentStream(APPEND) + 회전 행렬 + drawImage
  └─ doc.save(out)
```

### 10-2. 회전 행렬

페이지의 `/Rotate`에 따라 비트맵을 배치합니다. PDF 좌표계는 왼쪽 아래가 원점입니다.

| /Rotate | Matrix |
| --- | --- |
| 0 | `(1, 0, 0, 1, 0, 0)` |
| 90 | `(0, 1, -1, 0, pageWidth, 0)` |
| 180 | `(-1, 0, 0, -1, pageWidth, pageHeight)` |
| 270 | `(0, -1, 1, 0, 0, pageHeight)` |

### 10-3. 왜 벡터 주석이 아니라 래스터인가

- PDFBox-Android에서 한글 폰트를 임베드하려면 폰트 파일/서브셋 관리가 필요합니다.
- 형광펜 알파, 펜 곡선, 이모지/특수문자를 PDF 주석으로 옮기면 뷰어별 차이가 생깁니다.
- 화면과 **같은 Canvas 페인터**로 그린 이미지를 넣으면 WYSIWYG가 보장됩니다.
- 대신 주석이 있는 페이지만 이미지가 추가되어 파일 크기가 늘어납니다.
  - A4 2x 기준 약 1190×1684 PNG
  - 주석이 없는 페이지는 원본 벡터를 그대로 유지
- v0.2에서 "벡터 주석 + 폰트 임베드" 또는 "압축 이미지(JPEG/WebP)" 옵션을 검토합니다.

### 10-4. 파일명

`원본이름_주석.pdf`를 제안합니다. `\ / : * ? " < > |` 문자는 `_`로 치환하고 60자로 제한합니다.

---

## 11. 동시성과 메모리

- 모든 무거운 작업은 `Dispatchers.IO`에서 실행합니다.
- `renderJob`은 페이지 이동 시 취소합니다.
- `searchJob`은 입력이 바뀌면 취소하고 220ms 디바운스 후 실행합니다.
- `PdfRenderer` 접근은 `Mutex`로 직렬화합니다.
- 페이지 비트맵은 `LruCache`로 관리하고, 새 버전 조립 시 `evictAll()`합니다.
- `AndroidManifest`에 `largeHeap="true"`를 선언해 고해상도 페이지 렌더링 여유를 둡니다.
- export 비트맵은 페이지별로 만들고 즉시 `recycle()`합니다.

---

## 12. 오류 처리와 경계 조건

| 상황 | 동작 |
| --- | --- |
| 손상/암호화 PDF | `openDocument` 실패 → 홈으로 돌아가 오류 메시지 |
| 원본 페이지 수 감소 | `DocState.normalized()`가 잘못된 참조 제거 |
| 모든 페이지 삭제 | 마지막 1페이지는 삭제 불가(버튼 비활성) |
| 빈 페이지에 주석 | 저장은 되지만 초기화 시 제거(원본 페이지 없음) |
| 스캔 PDF 검색 | 결과 0 + "스캔 PDF는 검색이 제한될 수 있습니다" 안내 |
| 매우 큰 페이지 | 화면 렌더 폭 2400px, 내보내기 4096px로 상한 |
| SAF 쓰기 실패 | `export_failed` 메시지, 문서 상태는 유지 |
| 프로세스 사망 | 마지막 `persistNow()`까지 저장된 상태로 복원 |

---

## 13. 테스트 전략

### JVM 단위 테스트 (11개 통과)

- `PageSpecTest`: `normalizeRotation`의 음수/360도/450도 래핑, JSON 왕복
- `PageTransformsTest`: 90/180/270/0도 좌표 변환, 굵기·글자 크기 스케일,
  `textRotation` 누적, 음수 delta 정규화
- `DocStateTest`: 원본 페이지 수 유지, 누락 페이지 제거 + 인덱스 재매핑,
  유효 페이지 없음/원본 0페이지 fallback

```bash
./gradlew testDebugUnitTest
```

### 수동 스모크 (Pixel 7 AVD, Android 16)

- 필기 5종, 지우개, undo/redo
- 페이지 회전/이동/삭제/빈 페이지/초기화
- 회전된 페이지에서 텍스트·밑줄·형광펜 방향
- 메모 저장/목록, 검색/점프
- 내보내기 후 `pdfinfo` + Poppler 렌더 비교
- 내보낸 PDF를 앱에서 재열기
- **R8 릴리스 APK**에서 열기·필기·검색·회전·내보내기 재검증
- 릴리스 권한 확인, 네이티브 라이브러리 16 KB 정렬 확인

### v0.2에서 추가할 것

- 계측 테스트: 문서 열기 → 필기 → 회전 → 내보내기
- `buildHits` 검색 스니펫/대소문자/공백 정규화 테스트
- 골든 이미지: `AnnotationPainter` 출력 비교
- 대용량 PDF(500페이지), 저사양 기기, 다크 모드, 프로세스 사망 복원

---

## 14. 알려진 한계

- 기존 PDF의 텍스트를 직접 편집하거나 리플로우하지 않습니다.
- PDF 폼 필드, 디지털 서명, 기존 PDF 주석과의 상호작용은 지원하지 않습니다.
- 암호화된 PDF는 열 수 없습니다(오류 안내).
- 스캔 PDF OCR은 아직 없습니다.
- 텍스트 주석은 줄바꿈/폰트 1종만 지원합니다.
- 주석이 많은 페이지는 내보내기 파일이 커질 수 있습니다.
- 페이지 작업마다 전체 PDF를 재조립하므로 수백 페이지 문서에서는 느릴 수 있습니다.
- UI 계측 테스트가 아직 없습니다(JVM 단위 테스트만 존재).

---

## 15. 설계 결정 요약 (ADR)

| 결정 | 이유 | 대안 |
| --- | --- | --- |
| PDFBox-Android + `PdfRenderer` | 조립/텍스트 추출은 PDFBox, 화면 렌더는 빠른 플랫폼 API | PDFium 단독, MuPDF |
| 원본 + `PageSpec` 모델 | 원본 무결성, 페이지 작업 단순화 | 작업 PDF를 직접 변형 |
| 정규화 좌표 | 화면 크기/줌/해상도 독립 | 픽셀 좌표 |
| 공용 `AnnotationPainter` | 화면/내보내기 WYSIWYG | PDF 주석 API + 폰트 임베드 |
| JSON 파일 저장 | 단일 문서 상태, 마이그레이션 단순 | Room, SQLite |
| 단일 모듈 | 화면 2개, 작은 팀 | 멀티모듈 |
| 광고/인터넷 없음 | 신뢰·프라이버시 차별화 | AdMob, 구독 |
