package com.ktds.portal.approval.controller;

import com.ktds.portal.approval.dto.ApprovalResponse;
import com.ktds.portal.approval.dto.CreateApprovalRequest;
import com.ktds.portal.approval.dto.ProcessRequest;
import com.ktds.portal.approval.service.ApprovalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 결재 REST 컨트롤러.
 *
 * [리팩토링] docs/4-12 BL-04 — 요청 Map<String,Object> → record DTO, 응답 엔티티 노출 → ApprovalResponse:
 *  - 생성 요청 = CreateApprovalRequest, 처리 요청 = ProcessRequest, 응답 = ApprovalResponse(엔티티 비노출)
 * [보존 대상] 엔드포인트(URL·HTTP 메서드)·JSON 필드명·정수 status는 레거시와 100% 동일(계약 보존).
 *  action 매직넘버(1/2/3/9)를 API가 그대로 강요하는 것은 계약이라 유지(개선은 재설계 단계 몫).
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
        return ApprovalResponse.from(service.create(
                request.title(),
                request.content(),
                request.type(),
                request.priority(),
                request.drafterId(),
                request.approverId(),
                request.amount(),
                request.urgent()
        ));
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody ProcessRequest request) {
        service.processApproval(
                id,
                request.userId(),
                request.action(),
                request.reasonOrEmpty()   // [보존 대상] reason 미전송 시 "" (레거시 getOrDefault 동작)
        );
    }

    @GetMapping("/drafts/{userId}")
    public List<ApprovalResponse> drafts(@PathVariable Long userId) {
        return ApprovalResponse.fromList(service.myDrafts(userId));
    }

    @GetMapping("/inbox/{userId}")
    public List<ApprovalResponse> inbox(@PathVariable Long userId) {
        return ApprovalResponse.fromList(service.myInbox(userId));
    }
}
