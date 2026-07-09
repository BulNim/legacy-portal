package com.ktds.portal.approval;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.approval.service.ApprovalService;
import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [DI 효과 검증 단위 테스트] — MailSender 인터페이스 + 생성자 주입(BL-03)의 실익을 보여준다.
 *
 * 강결합(new SmtpMailSender) 시절엔 서비스가 실제 발송 구현을 직접 붙들고 있어 "메일이 실제로
 * 나가는지"를 테스트로 확인할 수 없었다. 이제 MailSender를 주입받으므로 콘솔/SMTP 대신
 * {@link FakeMailSender}(발송 대신 인자만 기록)를 꽂아, 실 발송 없이 "send가 언제·누구에게·어떤
 * 본문으로 호출되는지"를 검증한다.
 *
 * [안전망 불변] 이 파일은 신규 단위 테스트다. 기존 특성화 테스트(ApprovalServiceCharacterizationTest)는
 * 손대지 않는다 — 그쪽은 "레거시 동작 고정" 기준점, 이쪽은 "리팩토링으로 생긴 검증 가능성"을 다룬다.
 *
 * @DataJpaTest 슬라이스는 @Component 스캔을 하지 않으므로 협력 객체를 @Import로 직접 배선한다.
 * MailSender 구현은 FakeMailSender 하나뿐이라 ApprovalService도 이것을 주입받는다(발송 실물 없음).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApprovalService.class, ApprovalServiceMailTest.FakeMailSender.class, ApprovalServiceMailTest.NoOpAuditLogger.class})
class ApprovalServiceMailTest {

    @Autowired private ApprovalService approvalService;
    @Autowired private UserRepository userRepository;
    @Autowired private FakeMailSender fakeMailSender;

    private User 기안자;
    private User 결재자_팀장;
    private User 결재자_권한없음_사원;

    @BeforeEach
    void 준비() {
        기안자 = userRepository.save(new User("기안자", "drafter@test.com", 1, "개발팀"));
        결재자_팀장 = userRepository.save(new User("결재자", "approver@test.com", 2, "개발팀"));
        결재자_권한없음_사원 = userRepository.save(new User("무권한결재자", "noauth@test.com", 1, "개발팀"));
        // FakeMailSender는 스프링 싱글톤 빈이라 테스트 간 기록이 누적된다 → 매 테스트 시작 시 초기화.
        fakeMailSender.sentMails.clear();
    }

    @Test
    void 상신하면_결재자에게_결재요청_메일이_한_번_발송된다() {
        Approval 결재 = approvalService.create(
                "노트북 구매", "개발용 노트북", 1, 2,
                기안자.getId(), 결재자_팀장.getId(), 500_000L, false);

        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        FakeMailSender.SentMail 발송 = fakeMailSender.sentMails.get(0);
        assertThat(발송.to()).isEqualTo("approver@test.com");   // 결재자에게
        assertThat(발송.subject()).contains("결재요청");
    }

    @Test
    void 승인하면_기안자에게_결재승인_메일이_발송된다() {
        Approval 결재 = approvalService.create(
                "출장비 정산", "정산 요청", 1, 2,
                기안자.getId(), 결재자_팀장.getId(), 200_000L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sentMails.clear();   // 상신 발송은 준비 단계 → 승인 발송만 관찰

        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 2, "");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        FakeMailSender.SentMail 발송 = fakeMailSender.sentMails.get(0);
        assertThat(발송.to()).isEqualTo("drafter@test.com");   // 기안자에게
        assertThat(발송.subject()).contains("결재승인");
    }

    @Test
    void 반려하면_기안자에게_사유가_담긴_메일이_발송된다() {
        Approval 결재 = approvalService.create(
                "여름 휴가 신청", "7월 말 5일간", 2, 1,
                기안자.getId(), 결재자_팀장.getId(), 0L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sentMails.clear();

        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 3, "성수기라 반려");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        FakeMailSender.SentMail 발송 = fakeMailSender.sentMails.get(0);
        assertThat(발송.to()).isEqualTo("drafter@test.com");
        assertThat(발송.subject()).contains("결재반려");
        assertThat(발송.body()).contains("성수기라 반려");   // 본문에 반려 사유 포함
    }

    @Test
    void 취소는_메일을_발송하지_않는다() {
        Approval 결재 = approvalService.create(
                "비품 구매", "마우스 구매", 3, 1,
                기안자.getId(), 결재자_팀장.getId(), 30_000L, false);
        fakeMailSender.sentMails.clear();

        approvalService.processApproval(결재.getId(), 기안자.getId(), 9, "");

        assertThat(fakeMailSender.sentMails).isEmpty();   // 취소는 발송 없음(레거시 동작)
    }

    @Test
    void 권한없는_결재자의_승인_시도는_전이가_실패하므로_메일을_발송하지_않는다() {
        Approval 결재 = approvalService.create(
                "구매 요청", "테스트", 1, 2,
                기안자.getId(), 결재자_권한없음_사원.getId(), 300_000L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sentMails.clear();

        approvalService.processApproval(결재.getId(), 결재자_권한없음_사원.getId(), 2, "");

        // 도메인 전이 실패(return false) → 저장·메일·감사로그 등 부수효과 없음.
        assertThat(fakeMailSender.sentMails).isEmpty();
    }

    /**
     * 실 발송 대신 send() 호출 인자를 기록하는 테스트 대역(Test Double). MailSender 인터페이스 덕에
     * 프로덕션 코드 수정 없이 발송 구현만 이 Fake로 교체할 수 있다 — DI(BL-03)가 가능케 한 검증.
     */
    static class FakeMailSender implements MailSender {
        final List<SentMail> sentMails = new ArrayList<>();

        record SentMail(String to, String subject, String body) {}

        @Override
        public void send(String to, String subject, String body) {
            sentMails.add(new SentMail(to, subject, body));   // 발송하지 않고 인자만 캡처
        }
    }

    /** 이 테스트의 관심사는 메일뿐이라 감사 로그는 아무 것도 하지 않는 대역으로 대체(콘솔 소음 제거). */
    static class NoOpAuditLogger implements AuditLogger {
        @Override
        public void write(String action, Long id, Long userId) {
            // no-op
        }
    }
}
