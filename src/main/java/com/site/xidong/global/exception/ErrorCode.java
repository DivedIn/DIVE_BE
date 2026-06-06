package com.site.xidong.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 400
    INVALID_REFRESH_TOKEN(400, "리프레시 토큰이 유효하지 않습니다"),
    MISMATCH_REFRESH_TOKEN(400, "리프레시 토큰의 유저 정보가 일치하지 않습니다"),
    USER_ALREADY_EXIST(400, "이미 존재하는 유저입니다"),
    INVALID_PASSWORD(400, "비밀번호가 올바르지 않습니다"),
    INVALID_INPUT(400, "잘못된 입력값입니다"),
    // 401
    UNAUTHORIZED(401, "인증이 필요합니다"),
    TOKEN_EXPIRED(401, "토큰이 만료되었습니다"),
    TOKEN_INVALID(401, "유효하지 않은 토큰입니다"),
    INVALID_AUTH_TOKEN(401, "권한 정보가 없는 토큰입니다"),
    UNAUTHORIZED_MEMBER(401, "현재 내 계정 정보가 존재하지 않습니다"),
    // 403
    FORBIDDEN(403, "접근 권한이 없습니다"),
    // 404
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    MEMBER_NOT_FOUND(404, "해당 유저 정보를 찾을 수 없습니다"),
    REFRESH_TOKEN_NOT_FOUND(404, "로그아웃 된 사용자입니다"),
    ENTITY_NOT_FOUND(404, "일치하는 데이터가 없습니다"),
    QUESTION_NOT_FOUND(404, "질문을 찾을 수 없습니다"),
    QUESTION_SET_NOT_FOUND(404, "질문 세트를 찾을 수 없습니다"),
    VIDEO_NOT_FOUND(404, "영상을 찾을 수 없습니다"),
    // 409
    DUPLICATE_RESOURCE(409, "데이터가 이미 존재합니다"),
    EMAIL_ALREADY_EXISTS(409, "이미 사용 중인 이메일입니다"),
    // 500
    STT_TIMEOUT(500, "음성 변환 시간이 초과되었습니다"),
    STT_FAILED(500, "음성 변환에 실패했습니다"),
    AI_API_ERROR(500, "AI 피드백 생성에 실패했습니다"),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다");

    private final int status;
    private final String message;
}
