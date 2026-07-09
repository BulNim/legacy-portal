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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [순수 단위 테스트] 스프링 컨텍스트(@DataJpaTest) 없이 ApprovalService를 손으로 조립한다.
 *
 * 생성자 주입(BL-03)의 실익: 협력 객체를 외부에서 new 해 생성자에 직접 꽂을 수 있다.
 *  - MailSender → {@link FakeMailSender}  : 외부에서 new. 실 발송 대신 send() 인자만 기록.
 *  - AuditLogger → no-op 람다              : 이 테스트의 관심사가 아니라 무동작.
 *  - Repository  → Mockito mock            : DB 없이 findById 결과만 지정(조립 격리).
 *
 * 강결합(new SmtpMailSender) 시절엔 불가능했던 방식 — 컨테이너 기동 없이 서비스 로직만
 * 빠르게 격리 검증한다. 기존 특성화 테스트(안전망)와 @DataJpaTest 슬라이스 테스트는 건드리지 않는다.
 */
class ApprovalServiceMailUnitTest {

    private ApprovalRepository approvalRepository;
    private UserRepository userRepository;
    private FakeMailSender fakeMailSender;
    private ApprovalService approvalService;

    private User 기안자;
    private User 결재자_팀장;
    private User 결재자_권한없음_사원;

    @BeforeEach
    void 손으로_조립() {
        approvalRepository = mock(ApprovalRepository.class);
        userRepository = mock(UserRepository.class);
        fakeMailSender = new FakeMailSender();          // ← 외부에서 new
        AuditLogger 무동작로거 = (action, id, userId) -> { };   // no-op 감사 로거

        // 생성자에 협력 객체를 직접 주입 — 스프링 없이 순수 조립
        approvalService = new ApprovalService(approvalRepository, userRepository, fakeMailSender, 무동작로거);

        기안자 = 사용자(1L, "기안자", "drafter@test.com", 1);
        결재자_팀장 = 사용자(2L, "결재자", "approver@test.com", 2);
        결재자_권한없음_사원 = 사용자(3L, "무권한결재자", "noauth@test.com", 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(기안자));
        when(userRepository.findById(2L)).thenReturn(Optional.of(결재자_팀장));
        when(userRepository.findById(3L)).thenReturn(Optional.of(결재자_권한없음_사원));
    }

    @Test
    void 상신하면_결재자에게_결재요청_메일이_한_번_발송된다() {
        Approval 결재 = 결재(10L, 기안자.getId(), 결재자_팀장.getId(), 0);   // DRAFT
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(결재));

        approvalService.processApproval(10L, 기안자.getId(), 1, "");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        FakeMailSender.SentMail 발송 = fakeMailSender.sentMails.get(0);
        assertThat(발송.to()).isEqualTo("approver@test.com");
        assertThat(발송.subject()).contains("결재요청");
    }

    @Test
    void 승인하면_기안자에게_결재승인_메일이_발송된다() {
        Approval 결재 = 결재(11L, 기안자.getId(), 결재자_팀장.getId(), 1);   // SUBMITTED
        when(approvalRepository.findById(11L)).thenReturn(Optional.of(결재));

        approvalService.processApproval(11L, 결재자_팀장.getId(), 2, "");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        assertThat(fakeMailSender.sentMails.get(0).to()).isEqualTo("drafter@test.com");
        assertThat(fakeMailSender.sentMails.get(0).subject()).contains("결재승인");
    }

    @Test
    void 반려하면_기안자에게_사유가_담긴_메일이_발송된다() {
        Approval 결재 = 결재(12L, 기안자.getId(), 결재자_팀장.getId(), 1);   // SUBMITTED
        when(approvalRepository.findById(12L)).thenReturn(Optional.of(결재));

        approvalService.processApproval(12L, 결재자_팀장.getId(), 3, "성수기라 반려");

        assertThat(fakeMailSender.sentMails).hasSize(1);
        assertThat(fakeMailSender.sentMails.get(0).subject()).contains("결재반려");
        assertThat(fakeMailSender.sentMails.get(0).body()).contains("성수기라 반려");
    }

    @Test
    void 취소는_메일을_발송하지_않는다() {
        Approval 결재 = 결재(13L, 기안자.getId(), 결재자_팀장.getId(), 0);   // DRAFT
        when(approvalRepository.findById(13L)).thenReturn(Optional.of(결재));

        approvalService.processApproval(13L, 기안자.getId(), 9, "");

        assertThat(fakeMailSender.sentMails).isEmpty();
    }

    @Test
    void 권한없는_결재자의_승인은_전이가_실패하므로_메일을_발송하지_않는다() {
        Approval 결재 = 결재(14L, 기안자.getId(), 결재자_권한없음_사원.getId(), 1);   // SUBMITTED, 결재자=무권한
        when(approvalRepository.findById(14L)).thenReturn(Optional.of(결재));

        approvalService.processApproval(14L, 결재자_권한없음_사원.getId(), 2, "");

        assertThat(fakeMailSender.sentMails).isEmpty();   // 도메인 전이 실패(return false) → 부수효과 없음
    }

    // --- 테스트 픽스처 조립 헬퍼 --------------------------------------------------
    private User 사용자(Long id, String name, String email, int role) {
        User user = new User(name, email, role, "개발팀");
        user.setId(id);
        return user;
    }

    private Approval 결재(Long id, Long drafterId, Long approverId, int status) {
        Approval approval = new Approval();
        approval.setId(id);
        approval.setTitle("단위테스트 결재");
        approval.setType(1);
        approval.setStatus(status);
        approval.setPriority(2);
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(100_000L);
        return approval;
    }

    /**
     * 실 발송 대신 send() 인자를 기록하는 테스트 대역(Test Double).
     * 외부에서 {@code new FakeMailSender()} 로 만들어 서비스 생성자에 주입한다.
     */
    static class FakeMailSender implements MailSender {
        final List<SentMail> sentMails = new ArrayList<>();

        record SentMail(String to, String subject, String body) {}

        @Override
        public void send(String to, String subject, String body) {
            sentMails.add(new SentMail(to, subject, body));   // 발송하지 않고 인자만 캡처
        }
    }
}
