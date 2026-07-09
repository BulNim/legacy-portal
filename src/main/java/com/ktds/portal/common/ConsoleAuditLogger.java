package com.ktds.portal.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 감사 로그 기록기 — AuditLogger의 콘솔 출력 구현체(실습용).
 * [리팩토링] 인터페이스 AuditLogger 구현 + @Component 등록(docs/4-12 BL-03). 서비스는 이제 이 구체 타입을
 * 직접 new 하지 않고 AuditLogger 인터페이스로 주입받는다.
 * [이름 정리] 실제 파일 append를 쓰지 않고 콘솔에만 출력하므로 동작과 이름을 일치시켜 FileAuditLogger → ConsoleAuditLogger로 rename.
 *
 * [리팩토링] 세 서비스에 흩어져 있던 타임스탬프 생성 + "[now] ACTION id=X by=Y" 조립(중복 D1·D2·S1)을
 * 이 한 곳으로 모았다. 출력 형식이 바뀌어도 서비스는 손대지 않는다(포맷의 단일 소유지).
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void write(String action, Long id, Long userId) {
        // 실제 운영에서는 파일/DB에 append. 실습용으로 콘솔 출력.
        // [보존 대상] 출력 문자열 형식 "[AUDIT] [시각] ACTION id=X by=Y"는 레거시와 동일.
        String now = LocalDateTime.now().format(TIMESTAMP);
        System.out.println("[AUDIT] [" + now + "] " + action + " id=" + id + " by=" + userId);
    }
}
