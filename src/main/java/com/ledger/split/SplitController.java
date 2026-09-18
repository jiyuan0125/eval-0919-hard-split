package com.ledger.split;

import com.ledger.common.Money;
import com.ledger.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/splits")
public class SplitController {

    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    @PostMapping
    public BillResponse create(@CurrentUser Long userId, @Valid @RequestBody CreateBillRequest request) {
        long cents = Money.yuanToCents(request.total());
        BillDetail detail = splitService.createBill(userId, cents, request.participantIds(),
                request.description(), null);
        return BillResponse.from(detail);
    }

    @PostMapping("/{billId}/confirm")
    public BillResponse confirm(@CurrentUser Long userId, @PathVariable Long billId) {
        return BillResponse.from(splitService.confirm(userId, billId));
    }

    @PostMapping("/{billId}/withdraw")
    public BillResponse withdraw(@CurrentUser Long userId, @PathVariable Long billId,
                                 @RequestBody(required = false) ReasonRequest request) {
        String reason = request == null ? null : request.reason();
        return BillResponse.from(splitService.withdraw(userId, billId, reason));
    }

    @PostMapping("/{billId}/cancel")
    public BillResponse cancel(@CurrentUser Long userId, @PathVariable Long billId,
                               @RequestBody(required = false) ReasonRequest request) {
        String reason = request == null ? null : request.reason();
        return BillResponse.from(splitService.cancel(userId, billId, reason));
    }

    @PostMapping("/{billId}/reverse")
    public BillResponse reverse(@CurrentUser Long userId, @PathVariable Long billId,
                                @RequestBody(required = false) ReasonRequest request) {
        String reason = request == null ? null : request.reason();
        return BillResponse.from(splitService.reverse(userId, billId, reason));
    }

    @PostMapping("/{billId}/settle")
    public Map<String, Object> settle(@CurrentUser Long userId, @PathVariable Long billId) {
        Settlement settlement = splitService.settle(userId, billId);
        return Map.of("billId", settlement.getBillId(),
                "payerId", settlement.getPayerId(),
                "debtorId", settlement.getDebtorId(),
                "amount", Money.centsToYuan(settlement.getAmountCents()),
                "status", "SETTLED");
    }

    @GetMapping("/me/outstanding")
    public OutstandingResponse outstanding(@CurrentUser Long userId) {
        SplitService.Outstanding outstanding = splitService.outstanding(userId);
        List<Map<String, Object>> bills = outstanding.bills().stream()
                .map(line -> Map.<String, Object>of(
                        "billId", line.billId(),
                        "payerId", line.payerId(),
                        "share", Money.centsToYuan(line.shareCents()),
                        "outstanding", Money.centsToYuan(line.outstandingCents())))
                .toList();
        return new OutstandingResponse(Money.centsToYuan(outstanding.totalCents()), bills);
    }

    @GetMapping("/{billId}")
    public BillResponse get(@CurrentUser Long userId, @PathVariable Long billId) {
        return BillResponse.from(splitService.requireParticipantBill(userId, billId));
    }

    public record CreateBillRequest(@NotBlank String total,
                                    @NotEmpty List<Long> participantIds,
                                    String description) {
    }

    public record ReasonRequest(String reason) {
    }

    public record OutstandingResponse(String totalOutstanding, List<Map<String, Object>> bills) {
    }

    public record BillResponse(long id, long payerId, String total, String month,
                               String status, String description, Long predecessorId,
                               List<ParticipantResponse> participants,
                               List<Long> confirmedUserIds,
                               List<EventResponse> events,
                               List<SettlementResponse> settlements) {

        static BillResponse from(BillDetail detail) {
            List<ParticipantResponse> participants = detail.participants().stream()
                    .map(p -> new ParticipantResponse(p.getUserId(), Money.centsToYuan(p.getShareCents())))
                    .toList();
            List<EventResponse> events = detail.events().stream()
                    .map(e -> new EventResponse(e.getType().name(), e.getActorId(),
                            e.getDetail() == null ? "" : e.getDetail()))
                    .toList();
            List<SettlementResponse> settlements = detail.settlements().stream()
                    .map(s -> new SettlementResponse(s.getDebtorId(), Money.centsToYuan(s.getAmountCents())))
                    .toList();
            return new BillResponse(detail.bill().getId(), detail.bill().getPayerId(),
                    Money.centsToYuan(detail.bill().getTotalCents()), detail.bill().getMonth(),
                    detail.status().name(), detail.bill().getDescription() == null ? "" : detail.bill().getDescription(),
                    detail.bill().getPredecessorId(), participants, detail.confirmedUserIds(),
                    events, settlements);
        }
    }

    public record ParticipantResponse(long userId, String share) {
    }

    public record EventResponse(String type, long actorId, String detail) {
    }

    public record SettlementResponse(long debtorId, String amount) {
    }
}
