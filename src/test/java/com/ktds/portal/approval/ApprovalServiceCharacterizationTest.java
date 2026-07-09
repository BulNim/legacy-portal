package com.ktds.portal.approval;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.approval.service.ApprovalService;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * [리팩토링 전 안전망] ApprovalService.processApproval(id, userId, action, reason)의
 * 현재(레거시) 동작을 고정하는 특성화 테스트(Characterization Test).
 *
 * 옳고 그름을 판단하지 않고 "지금 이렇게 동작한다"는 사실만 기록한다(docs/CLAUDE.md
 * "보존 대상 vs 개선 대상" 참고). 리팩토링 전후로 이 6개 테스트는 수정 없이 그대로
 * green이어야 한다 — 호출·기대값·DB값이 동일해야 검증된 것으로 본다.
 *
 * @DataJpaTest는 커스텀 DataSourceConfig(@Configuration)를 스캔하지 않으므로,
 * replace=NONE으로 명시해 src/test/resources/application.properties에 이미 지정된
 * H2(MODE=LEGACY) 설정이 자동 치환(랜덤 임베디드 DB)되지 않도록 고정한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApprovalService.class)
class ApprovalServiceCharacterizationTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User 기안자;
    private User 결재자_팀장;
    private User 결재자_권한없음_사원;

    @BeforeEach
    void 사용자_시드() {
        기안자 = userRepository.save(new User("기안자", "drafter@test.com", 1, "개발팀"));
        결재자_팀장 = userRepository.save(new User("결재자", "approver@test.com", 2, "개발팀"));
        결재자_권한없음_사원 = userRepository.save(new User("무권한결재자", "noauth@test.com", 1, "개발팀"));
    }

    @Test
    void 상신_후_승인하면_상태가_임시저장에서_상신을_거쳐_승인으로_바뀐다() {
        Approval 결재 = approvalService.create(
                "노트북 구매", "개발용 노트북", 1, 2,
                기안자.getId(), 결재자_팀장.getId(), 500_000L, false);
        assertThat(결재.getStatus()).isEqualTo(0);

        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        Approval 상신됨 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(상신됨.getStatus()).isEqualTo(1);

        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 2, "");
        Approval 승인됨 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(승인됨.getStatus()).isEqualTo(2);
    }

    @Test
    void 상신된_결재를_반려하면_상태가_반려로_바뀌고_반려사유가_저장된다() {
        Approval 결재 = approvalService.create(
                "여름 휴가 신청", "7월 말 5일간", 2, 1,
                기안자.getId(), 결재자_팀장.getId(), 0L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");

        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 3, "성수기라 반려");

        Approval 반려됨 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(반려됨.getStatus()).isEqualTo(3);
        assertThat(반려됨.getRejectReason()).isEqualTo("성수기라 반려");
    }

    @Test
    void 임시저장_상태에서_기안자가_취소하면_상태가_취소로_바뀐다() {
        Approval 결재 = approvalService.create(
                "비품 구매", "마우스 구매", 3, 1,
                기안자.getId(), 결재자_팀장.getId(), 30_000L, false);

        approvalService.processApproval(결재.getId(), 기안자.getId(), 9, "");

        Approval 취소됨 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(취소됨.getStatus()).isEqualTo(9);
    }

    @Test
    void 권한없는_결재자가_승인을_시도하면_예외_없이_조용히_무시되어_상태가_유지된다() {
        Approval 결재 = approvalService.create(
                "구매 요청 테스트", "테스트", 1, 2,
                기안자.getId(), 결재자_권한없음_사원.getId(), 300_000L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");

        assertDoesNotThrow(() ->
                approvalService.processApproval(결재.getId(), 결재자_권한없음_사원.getId(), 2, ""));

        Approval 여전히_상신 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(여전히_상신.getStatus()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_결재_id로_처리를_시도하면_예외_없이_아무_일도_일어나지_않는다() {
        long 존재하지_않는_id = 999_999_999L;

        assertDoesNotThrow(() ->
                approvalService.processApproval(존재하지_않는_id, 기안자.getId(), 1, ""));

        assertThat(approvalRepository.findById(존재하지_않는_id)).isEmpty();
    }

    @Test
    void 이미_승인된_결재를_다시_승인해도_상태가_승인으로_그대로_유지된다() {
        Approval 결재 = approvalService.create(
                "출장비 정산", "테스트", 1, 2,
                기안자.getId(), 결재자_팀장.getId(), 200_000L, false);
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 2, "");

        Approval 첫_승인_직후 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(첫_승인_직후.getStatus()).isEqualTo(2);
        LocalDateTime 첫_승인_시각 = 첫_승인_직후.getUpdatedAt();

        approvalService.processApproval(결재.getId(), 결재자_팀장.getId(), 2, "");

        Approval 재승인_시도_후 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(재승인_시도_후.getStatus()).isEqualTo(2);
        assertThat(재승인_시도_후.getUpdatedAt()).isEqualTo(첫_승인_시각);
    }
}
