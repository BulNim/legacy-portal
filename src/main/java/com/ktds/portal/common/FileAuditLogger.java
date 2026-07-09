package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기 — AuditLogger의 파일(실습용 콘솔) 구현체.
 * [리팩토링] 인터페이스 AuditLogger 구현 + @Component 등록(docs/4-12 BL-03). 서비스는 이제 이 구체 타입을
 * 직접 new 하지 않고 AuditLogger 인터페이스로 주입받는다. 실습에서는 실제 파일 append 대신 콘솔에 출력만 한다.
 */
@Component
public class FileAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
