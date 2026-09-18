package com.example.splitbill.split;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/splits")
public class SplitController {

    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    public record CreateBillRequest(@NotNull Long payerId, @NotBlank String title,
                                    @Positive long totalCents,
                                    @NotEmpty List<Long> friendIds) {
    }

    public record ParticipantActionRequest(@NotNull Long userId) {
    }

    public record ReverseBillRequest(String reason) {
    }

    public record ParticipantView(Long userId, long shareCents, ParticipantStatus status) {
    }

    public record BillView(Long id, Long payerId, String title, long totalCents,
                           long payerShareCents, BillStatus status,
                           List<ParticipantView> participants) {
        static BillView of(SplitBill bill) {
            return new BillView(bill.getId(), bill.getPayerId(), bill.getTitle(),
                    bill.getTotalCents(), bill.getPayerShareCents(), bill.getStatus(),
                    bill.getParticipants().stream()
                            .map(p -> new ParticipantView(p.getUserId(), p.getShareCents(), p.getStatus()))
                            .toList());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillView create(@Valid @RequestBody CreateBillRequest request) {
        return BillView.of(splitService.createBill(
                request.payerId(), request.title(), request.totalCents(), request.friendIds()));
    }

    @GetMapping("/{billId}")
    public BillView get(@PathVariable Long billId) {
        return BillView.of(splitService.getBill(billId));
    }

    @PostMapping("/{billId}/confirm")
    public BillView confirm(@PathVariable Long billId,
                            @Valid @RequestBody ParticipantActionRequest request) {
        return BillView.of(splitService.confirm(billId, request.userId()));
    }

    @PostMapping("/{billId}/withdraw")
    public BillView withdraw(@PathVariable Long billId,
                             @Valid @RequestBody ParticipantActionRequest request) {
        return BillView.of(splitService.withdraw(billId, request.userId()));
    }

    @PostMapping("/{billId}/reverse")
    public BillView reverse(@PathVariable Long billId,
                            @RequestBody(required = false) ReverseBillRequest request) {
        String reason = request != null && request.reason() != null ? request.reason() : "manual correction";
        return BillView.of(splitService.reverseBill(billId, reason));
    }
}
