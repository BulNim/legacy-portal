package com.ktds.portal.common;

/**
 * [리팩토링] 감사 로그 기록 추상화 (docs/4-12 BL-03). 구현체(ConsoleAuditLogger)를 직접 new 하던 강결합을
 * 이 인터페이스 + 생성자 주입으로 끊는다 — 출력 대상(파일/DB/콘솔) 교체 시 호출부 불변(DIP).
 *
 * [리팩토링] 세 서비스(Approval/Notice/Schedule)에 복붙되던 타임스탬프 생성 + "[now] ACTION id=X by=Y"
 * 문자열 조립(중복 D1·D2·S1)을 이 추상화 뒤로 위임한다. 서비스는 "무엇을(action)·어디에(id)·누가(userId)"만
 * 넘기고, 시각 포맷·출력 형식은 구현체가 소유한다.
 */
public interface AuditLogger {
    void write(String action, Long id, Long userId);
}
