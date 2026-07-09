package com.ktds.portal.common;

/**
 * [리팩토링] 감사 로그 기록 추상화 (docs/4-12 BL-03). 구현체(FileAuditLogger)를 직접 new 하던 강결합을
 * 이 인터페이스 + 생성자 주입으로 끊는다 — 출력 대상(파일/DB/콘솔) 교체 시 호출부 불변(DIP).
 * [보존 대상] write(line) 시그니처·동작은 레거시 FileAuditLogger와 동일.
 */
public interface AuditLogger {
    void write(String line);
}
