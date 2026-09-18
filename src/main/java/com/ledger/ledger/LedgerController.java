package com.ledger.ledger;

import com.ledger.common.Money;
import com.ledger.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/entries")
    public Map<String, Object> create(@CurrentUser Long userId,
                                      @Valid @RequestBody CreateEntryRequest request) {
        long cents = Money.yuanToCents(request.amount());
        LedgerEntry entry = ledgerService.bookManualExpense(userId, cents, request.description());
        return toMap(entry);
    }

    @GetMapping("/entries")
    public List<Map<String, Object>> list(@CurrentUser Long userId,
                                          @RequestParam(name = "month", required = false) String month) {
        String key = month != null ? month : ledgerService.currentMonth();
        return ledgerService.entries(userId, key).stream().map(LedgerController::toMap).toList();
    }

    static Map<String, Object> toMap(LedgerEntry entry) {
        return Map.of(
                "id", entry.getId(),
                "userId", entry.getUserId(),
                "month", entry.getMonth(),
                "amount", Money.centsToYuan(entry.getAmountCents()),
                "type", entry.getType().name(),
                "splitId", entry.getSplitId() == null ? 0L : entry.getSplitId(),
                "refEntryId", entry.getRefEntryId() == null ? 0L : entry.getRefEntryId(),
                "counterpartyId", entry.getCounterpartyId() == null ? 0L : entry.getCounterpartyId(),
                "description", entry.getDescription() == null ? "" : entry.getDescription());
    }

    public record CreateEntryRequest(@NotBlank String amount, String description) {
    }
}
