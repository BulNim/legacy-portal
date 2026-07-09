package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기 — MailSender의 SMTP(실습용 콘솔) 구현체.
 * [리팩토링] 인터페이스 MailSender 구현 + @Component 등록(docs/4-12 BL-03). 서비스는 이제 이 구체 타입을
 * 직접 new 하지 않고 MailSender 인터페이스로 주입받는다. 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 */
@Component
public class SmtpMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
