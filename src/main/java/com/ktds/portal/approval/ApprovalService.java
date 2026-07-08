package com.ktds.portal.approval;

import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.Role;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
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
 *  나머지(God Class, 메일/감사로그 복붙, 승인/반려 권한판정 복붙, amountGrade()의 등급 기준값 등)는 아직 그대로다.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // [스멜8] 파라미터 8개.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);   // type은 int 파라미터 그대로(매직넘버 아님)
        approval.setPriority(urgent ? Priority.HIGH.code() : priority);   // [리팩토링] 매직넘버 3 → Priority.HIGH.code()
        approval.setStatus(ApprovalStatus.DRAFT.code());   // [리팩토링] 매직넘버 0 → ApprovalStatus.DRAFT.code() (DB엔 여전히 0 저장)
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();   // getType()은 int — 감사 로그 정수 출력 레거시 그대로
        audit.write(line);
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
            case CANCEL -> cancel(approval, userId);
        }
    }

    // [리팩토링] 상신 분기 추출. [상태 가드] 임시저장(DRAFT)일 때만 진행 — 아니면 조용히 무시(레거시 동작 보존).
    private void submit(Approval approval, Long userId) {
        if (ApprovalStatus.fromCode(approval.getStatus()) != ApprovalStatus.DRAFT) {
            return;
        }
        // [스멜6] 금액 기준 우선순위 자동 상향 — 도메인 규칙이 서비스에 박혀 있다(구조는 레거시 그대로).
        // 금액 임계값 1000000은 범주형이 아니라 그대로 둔다.
        if (ApprovalType.fromCode(approval.getType()) == ApprovalType.EXPENSE && approval.getAmount() >= 1000000) {
            approval.setPriority(Priority.HIGH.code());
        }
        approval.setStatus(ApprovalStatus.SUBMITTED.code());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙(중복 제거는 별도 단계).
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            String body = "안녕하세요 " + approver.getName() + "님,\n"
                    + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                    + "\n기안자ID: " + approval.getDrafterId();
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL SUBMIT", approval.getId(), userId);
    }

    // [리팩토링] 승인 분기 추출. [권한 가드] 상신 상태 + 결재자 본인 + 팀장 이상, 하나라도 아니면 조용히 무시.
    private void approve(Approval approval, User actor, Long userId) {
        if (ApprovalStatus.fromCode(approval.getStatus()) != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (actor.getRole() < Role.MANAGER.code()) {   // role>=2(팀장 이상)이 아니면 무시
            return;
        }
        approval.setStatus(ApprovalStatus.APPROVED.code());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        // [스멜4] 또 복붙된 메일 발송
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL APPROVE", approval.getId(), userId);
    }

    // [리팩토링] 반려 분기 추출. [권한 가드] 승인과 동일 판정(조건식 복붙은 여전히 남아있음, docs/4-5 4절).
    private void reject(Approval approval, User actor, Long userId, String reason) {
        if (ApprovalStatus.fromCode(approval.getStatus()) != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(userId)) {
            return;
        }
        if (actor.getRole() < Role.MANAGER.code()) {
            return;
        }
        approval.setStatus(ApprovalStatus.REJECTED.code());
        approval.setRejectReason(reason);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                    + "\n사유: " + reason;
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL REJECT", approval.getId(), userId);
    }

    // [리팩토링] 취소 분기 추출. [권한 가드] 기안자 본인 + 아직 승인 전(DRAFT/SUBMITTED)일 때만.
    private void cancel(Approval approval, Long userId) {
        ApprovalStatus status = ApprovalStatus.fromCode(approval.getStatus());
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return;
        }
        if (approval.getDrafterId() == null || !approval.getDrafterId().equals(userId)) {
            return;
        }
        approval.setStatus(ApprovalStatus.CANCELED.code());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        writeAudit("APPROVAL CANCEL", approval.getId(), userId);
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [리팩토링] tmp 임시변수 + 5분기 if 제거 — ApprovalStatus가 라벨을 스스로 가지므로 위임만 한다.
    // [스멜1] statusLabel 자체는 여전히 서비스에 남아있다(화면 표시용 문자열을 서비스가 만드는 책임은 미해소).
    public String statusLabel(Approval approval) {
        return ApprovalStatus.fromCode(approval.getStatus()).label();
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval approval) {
        long a = approval.getAmount();   // a = amount(금액, 원)  [스멜9: 한 글자 약어]
        if (a >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (a >= 1000000) return "A";   // 100만원=A
        else if (a >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
