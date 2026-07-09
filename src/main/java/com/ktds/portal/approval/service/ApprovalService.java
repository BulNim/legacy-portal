package com.ktds.portal.approval.service;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalAction;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.domain.ApprovalPriority;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : (해소) 과거 new SmtpMailSender()/new FileAuditLogger() 직접 생성 → MailSender/AuditLogger 주입.
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).
 * =================================================================================
 *
 * [리팩토링 진행] docs/4-12 BL-02·BL-09:
 *  - (항목 3·7) 매직넘버 → enum: Approval.status/type/priority, User.role 필드를 enum + AttributeConverter로
 *    전환(DB엔 정수 그대로 저장). getter/setter는 int 시그니처를 유지해 public 계약·JSON·특성화 테스트 불변.
 *    action은 저장 필드가 아니라 파라미터라 서비스에서 ApprovalAction.fromCode로만 다룬다.
 *  - (항목 2) Long Method: processApproval()을 guard clause + submit/approve/reject/cancel private 메서드로 분해.
 *  - (항목 9) 약어 지역변수 d/u/s/proc → approval/actor/status로 rename, proc 제거하고 action 직접 사용.
 *  - (항목 9·10) statusLabel()의 tmp+5분기 if 제거 → ApprovalStatus.label()에 위임.
 *  - (항목 6) statusLabel·amountGrade를 Approval 도메인으로 Move Method(amountGrade는 AmountGrade enum) → Feature Envy 해소.
 *  - (항목 5) 강결합 해소: SmtpMailSender/FileAuditLogger 직접 new → MailSender/AuditLogger 인터페이스 생성자 주입(BL-03).
 *  - (항목 1·6) 상태전이·권한 규칙을 Approval 도메인으로 이동(submit/approve/reject/cancel, 위반 시 return false) →
 *    서비스는 "도메인 전이 성공 시에만 저장·메일·감사로그" 오케스트레이션만 담당(if-지옥 제거).
 *  나머지(메일 본문·감사로그 조립이 아직 서비스 private, create()의 감사로그 복붙)는 다음 단계.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [리팩토링] BL-03 — 직접 new(강결합) 제거. 발송·기록을 인터페이스로 추상화해 생성자 주입(DIP).
    //           테스트에서 가짜 MailSender/AuditLogger로 교체 가능해진다.
    private final MailSender mail;
    private final AuditLogger audit;

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo,
                           MailSender mail, AuditLogger audit) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mail = mail;
        this.audit = audit;
    }

    // [스멜8] 파라미터 8개.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);   // type은 int 파라미터 그대로(매직넘버 아님)
        approval.setPriority(urgent ? ApprovalPriority.HIGH.code() : priority);   // [리팩토링] 매직넘버 3 → ApprovalPriority.HIGH.code()
        approval.setStatus(ApprovalStatus.DRAFT.code());   // [리팩토링] 매직넘버 0 → ApprovalStatus.DRAFT.code() (DB엔 여전히 0 저장)
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [리팩토링] 타임스탬프 생성+문자열 조립을 AuditLogger로 위임(중복 D1·D2·S1 제거). 세 서비스 CREATE 로그가 일관해진다.
        audit.write("APPROVAL CREATE", approval.getId(), drafterId);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     *
     * [리팩토링] docs/4-12 BL-09 — Long Method 분해:
     *  - 초기 조회 실패(null)를 guard clause로 조기 반환.
     *  - action별 처리를 submit/approve/reject/cancel private 메서드로 추출.
     *  - 외부 시그니처·관찰 동작은 그대로(조건 불만족 시 예외 없이 조용히 무시하는 레거시 동작 포함).
     * [보존 대상] public 시그니처 processApproval(id, userId, action, reason) 유지 — 내부에서 위임만 한다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        // [리팩토링] guard clause — 조회 실패는 조기 반환(레거시의 "조용히 무시" 동작 보존).
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            return;
        }
        User actor = userRepo.findById(userId).orElse(null);
        if (actor == null) {
            return;
        }

        // [리팩토링] action 매직넘버 → ApprovalAction으로 변환한 뒤 switch로 분기.
        // [동작 보존] 정의되지 않은 action은 fromCode()가 null → guard로 조기 반환(레거시의 "조용히 무시").
        //            switch(null)은 NPE를 던지므로 반드시 switch 앞에서 걸러낸다.
        ApprovalAction requestedAction = ApprovalAction.fromCode(action);
        if (requestedAction == null) {
            return;
        }
        switch (requestedAction) {
            case SUBMIT -> submit(approval, userId);
            case APPROVE -> approve(approval, actor, userId);
            case REJECT -> reject(approval, actor, userId, reason);
            case CANCEL -> cancel(approval, actor, userId);
        }
    }

    // [리팩토링] 상태전이·권한은 Approval 도메인으로 이동. 서비스는 전이 성공(true)일 때만 부수효과(저장·메일·감사로그) 수행.
    private void submit(Approval approval, Long userId) {
        if (!approval.submit()) {
            return;   // 상태 가드 위반 → 조용히 무시(레거시 동작 보존)
        }
        repo.save(approval);
        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙(중복 제거는 별도 단계).
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), submittedBody(approver, approval));
        }
        audit.write("APPROVAL SUBMIT", approval.getId(), userId);
    }

    // [리팩토링] 승인 — 상태전이·권한 판정은 approval.approve(actor)에 위임. 성공 시에만 부수효과.
    private void approve(Approval approval, User actor, Long userId) {
        if (!approval.approve(actor)) {
            return;
        }
        repo.save(approval);
        // [스멜4] 또 복붙된 메일 발송
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), approvedBody(drafter, approval));
        }
        audit.write("APPROVAL APPROVE", approval.getId(), userId);
    }

    // [리팩토링] 반려 — approval.reject(actor, reason)에 위임. 성공 시에만 부수효과.
    private void reject(Approval approval, User actor, Long userId, String reason) {
        if (!approval.reject(actor, reason)) {
            return;
        }
        repo.save(approval);
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), rejectedBody(drafter, approval, reason));
        }
        audit.write("APPROVAL REJECT", approval.getId(), userId);
    }

    // [리팩토링] 취소 — approval.cancel(actor)에 위임. 성공 시에만 부수효과(취소는 메일 없음, 레거시 그대로).
    private void cancel(Approval approval, User actor, Long userId) {
        if (!approval.cancel(actor)) {
            return;
        }
        repo.save(approval);
        audit.write("APPROVAL CANCEL", approval.getId(), userId);
    }

    // [리팩토링] submit()의 메일 본문 조립을 Extract Method(본문은 서비스 private 유지).
    //           [보존 대상] 반환 문자열은 레거시와 100% 동일(기안자ID 포함).
    private String submittedBody(User approver, Approval approval) {
        return "안녕하세요 " + approver.getName() + "님,\n"
                + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                + "\n기안자ID: " + approval.getDrafterId();
    }

    // [리팩토링] approve()의 메일 본문 조립을 Extract Method(본문은 서비스 private 유지).
    //           [보존 대상] 반환 문자열은 레거시와 100% 동일.
    private String approvedBody(User drafter, Approval approval) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
    }

    // [리팩토링] reject()의 메일 본문 조립을 Extract Method(본문은 서비스 private 유지).
    //           [보존 대상] 반환 문자열은 레거시와 100% 동일(사유 포함).
    private String rejectedBody(User drafter, Approval approval, String reason) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                + "\n사유: " + reason;
    }

    // [리팩토링] 감사 로그 기록은 AuditLogger.write(action, id, userId)로 완전히 위임 —
    //           타임스탬프 생성·문자열 조립이 서비스에서 사라졌다(중복 D1·D2·S1 제거, 예전 writeAudit private 삭제).

    // [리팩토링] statusLabel(표현 규칙)·amountGrade(도메인 규칙)는 Approval 도메인으로 Move Method 완료.
    //           → Approval.statusLabel() / Approval.amountGrade()(AmountGrade enum 위임). 서비스에서 제거해 Feature Envy 해소.

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
