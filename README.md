<a href="https://eclectic-biscuit-0eca48.netlify.app/"> <img src="https://github.com/minyeongg/dive-server/blob/main/assets/dive_logo.png" align="left" width="100"></a>
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
Java 17, Spring Boot 3.3, Spring Data JPA, MySQL 8.0, AWS EC2, Github Actions, Swagger

## 주요 화면

### 면접 질문 목록
> 공개된 면접 질문 목록을 조회하고, 카테고리별로 탐색하거나 원하는 질문을 내 세트에 추가할 수 있는 화면입니다.
<img src="https://github.com/minyeongg/dive-server/blob/main/assets/question.png">

---

### 면접 질문 상세
<img src="https://github.com/minyeongg/dive-server/blob/main/assets/questionSet.png">

---

### 면접 연습 영상 녹화
> 모의 면접 화면입니다.

<img src="https://github.com/minyeongg/dive-server/blob/main/assets/image.png">

---

### 내 녹화 영상 목록
> 내가 녹화한 면접 답변 영상들을 모아볼 수 있는 화면입니다.

<img src="https://github.com/minyeongg/dive-server/blob/main/assets/list.jpeg">

---

### AI 피드백 결과
> 녹화된 답변 영상을 STT(음성→텍스트)로 변환한 후, Claude API를 통해 내용 전달력·논리 구성 측면의 피드백을 생성하고 결과를 보여주는 화면입니다.

<img src="https://github.com/minyeongg/dive-server/blob/main/assets/feedback.png">

---

## 시스템 아키텍처
<img src="https://github.com/minyeongg/dive-server/blob/main/assets/dive_service_architecture.drawio_(1).png">

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
