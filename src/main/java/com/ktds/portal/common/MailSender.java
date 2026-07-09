package com.ktds.portal.common;

/**
 * [리팩토링] 메일 발송 추상화 (docs/4-12 BL-03). 구현체(ConsoleMailSender)를 직접 new 하던 강결합을
 * 이 인터페이스 + 생성자 주입으로 끊는다 — 테스트에서 가짜 구현으로 교체 가능(DIP).
 * [보존 대상] send(to, subject, body) 시그니처·동작은 레거시 구현체와 동일.
 */
public interface MailSender {
    void send(String to, String subject, String body);
}
