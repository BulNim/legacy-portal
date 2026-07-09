package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기 — MailSender의 콘솔 출력 구현체(실습용).
 * [리팩토링] 인터페이스 MailSender 구현 + @Component 등록(docs/4-12 BL-03). 서비스는 이제 이 구체 타입을
 * 직접 new 하지 않고 MailSender 인터페이스로 주입받는다.
 * [이름 정리] 실제 SMTP를 쓰지 않고 콘솔에만 출력하므로 동작과 이름을 일치시켜 SmtpMailSender → ConsoleMailSender로 rename.
 */
@Component
public class ConsoleMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제 운영에서는 JavaMailSender 등으로 대체. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
