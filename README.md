ㅋ# DIVE <a href="https://eclectic-biscuit-0eca48.netlify.app/"> <img src="https://github.com/minyeongg/dive-server/blob/main/assets/dive_logo.png" align="left" width="100"></a>
### 개발자를 위한 기술면접 연습 피드백 서비스
https://eclectic-biscuit-0eca48.netlify.app/
<br>

## 주요 기능
- 면접 질문 제작
- 면접 질문 세트 제작
- 다른 사용자의 질문/세트 가져오기
- 면접 연습 영상 녹화하기
- Claude 및 다른 사용자의 피드백 받기
- 소셜로그인

## Developers
**Front-end** : <a href="https://github.com/skdltn210">고은수</a> <br>
**Back-end** : <a href="https://github.com/minyeongg">박민영</a>

## Tech Stack
**Language**
- Java 17 <br>

**Framework** - <img src="https://img.shields.io/badge/SpringBoot-6DB33F?style=for-the-social&logo=SpringBoot&logoColor=white">  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-social&logo=Gradle&logoColor=white"> <img src="https://img.shields.io/badge/Lombok-CA0C00?style=for-the-social&logo=Java&logoColor=white">

**ORM** - <img src="https://img.shields.io/badge/Spring Data JPA-6DB33F?style=for-the-social&logo=Databricks&logoColor=white">

**Authorization** - <img src="https://img.shields.io/badge/Spring Security-6DB33F?style=for-the-social&logo=springsecurity&logoColor=white">  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-social&logo=JSON%20Web%20Tokens&logoColor=white">

**Database** - <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-social&logo=MySQL&logoColor=white">

**Cloud** - <img src ="https://img.shields.io/badge/AWS EC2-FF9900?style=for-the-social&logo=amazonec2&logoColor=white">  <img src ="https://img.shields.io/badge/AWS S3-569A31?style=for-the-social&logo=amazons3&logoColor=white">  <img src="https://img.shields.io/badge/CloudFront-232F3E?style=for-the-social&logo=Amazon%20AWS&logoColor=white">

**Api Docs** - <img src="https://img.shields.io/badge/Swagger-85EA2D?style=for-the-social&logo=swagger&logoColor=white"> <img src="https://img.shields.io/badge/Spring REST Docs-6DB33F?style=for-the-social&logo=Spring&logoColor=white">

**Tools** - <img src="https://img.shields.io/badge/FFmpeg-007808?style=for-the-social&logo=ffmpeg&logoColor=white"> <img src="https://img.shields.io/badge/AWS%20Transcribe-FF9900?style=for-the-social&logo=Amazon%20AWS&logoColor=white"> <img src="https://img.shields.io/badge/Claude%20API-5728D7?style=for-the-social&logo=Anthropic&logoColor=white">


## 주요 화면

### 면접 질문 목록
> 공개된 면접 질문 목록을 조회하고, 카테고리별로 탐색하거나 원하는 질문을 내 세트에 추가할 수 있는 화면입니다.

<!-- 스크린샷 추가 예정 -->

---

### 면접 질문 상세
> 선택한 질문의 내용과 해당 질문에 달린 다른 사용자들의 답변 영상 및 댓글을 확인할 수 있는 화면입니다.

<!-- 스크린샷 추가 예정 -->

---

### 면접 연습 영상 녹화
> 질문을 보며 웹캠으로 답변 영상을 녹화하는 화면입니다. 녹화된 영상은 S3에 업로드되고 이후 AI 피드백 생성에 활용됩니다.

<!-- 스크린샷 추가 예정 -->

---

### 인터뷰 세션
> 질문 세트를 기반으로 순서대로 답변을 진행하는 모의 면접 화면입니다. 질문 전환 및 녹화 흐름을 연속적으로 경험할 수 있습니다.

<!-- 스크린샷 추가 예정 -->

---

### 내 녹화 영상 목록
> 내가 녹화한 면접 답변 영상들을 모아볼 수 있는 화면입니다. 각 영상에 대한 AI 피드백 생성 여부와 상태를 확인할 수 있습니다.

<!-- 스크린샷 추가 예정 -->

---

### AI 피드백 결과
> 녹화된 답변 영상을 STT(음성→텍스트)로 변환한 후, Claude API를 통해 내용 전달력·논리 구성 측면의 피드백을 생성하고 결과를 보여주는 화면입니다.

<!-- 스크린샷 추가 예정 -->

---

## 시스템 아키텍처

<!-- 아키텍처 다이어그램 추가 예정 -->

```
[Client (React / Netlify)]
        │ HTTPS
        ▼
[Spring Boot API Server (AWS EC2)]
   ├── Spring Security + JWT 인증
   ├── 소셜 로그인 (Kakao / Naver OAuth2)
   ├── 질문·질문세트 관리 API
   ├── 영상 업로드 & 처리 파이프라인
   │     ├── FFmpeg - 영상 변환/썸네일 추출
   │     ├── AWS S3 - 영상·썸네일 저장
   │     ├── AWS Transcribe / Whisper - STT 변환
   │     └── DB 기반 비동기 처리 큐 (VideoProcessingQueue)
   ├── Claude API - AI 피드백 생성
   ├── SSE (Server-Sent Events) - 실시간 처리 상태 알림
   └── 댓글·피드백 관리 API
        │
        ▼
[AWS RDS (MySQL)]       [AWS S3 + CloudFront]
```

## 패키지 구조

```
src/main/java/com/site/xidong/
├── config/           # 보안, AWS, Swagger, 비동기 등 애플리케이션 설정
├── security/         # JWT 인증 필터, 소셜 로그인(Kakao/Naver) 처리
├── siteUser/         # 회원 도메인 (엔티티, 서비스, 컨트롤러)
├── question/         # 면접 질문 도메인
├── questionSet/      # 면접 질문 세트 도메인
├── video/            # 영상 업로드·조회·처리 도메인 (FFmpeg, S3, Presigned URL)
├── feedback/         # AI 피드백 도메인 (Claude API 호출, AWS Transcribe / Whisper STT)
├── comment/          # 영상 댓글 도메인
├── notification/     # SSE 기반 실시간 알림
├── queue/            # DB 기반 영상 처리 비동기 큐 (VideoProcessingQueue, Scheduler)
├── utils/            # S3 업로더, 에러 응답 공통 유틸
└── exception/        # 공통 예외 처리
```

## Database Schema
<img src="https://github.com/minyeongg/dive-server/blob/main/assets/dive_erd.png" width="900">