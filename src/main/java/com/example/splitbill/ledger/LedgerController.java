package com.example.splitbill.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    public LedgerController(LedgerService ledgerService, LedgerRepository ledgerRepository) {
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
    }

    public record ExpenseRequest(@NotNull Long userId, @Positive long amountCents,
                                 @NotNull LocalDate occurredOn, String note) {
    }

    public record ReverseRequest(String reason) {
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntry recordExpense(@Valid @RequestBody ExpenseRequest request) {
        return ledgerService.recordExpense(
                request.userId(), request.amountCents(), request.occurredOn(), request.note());
    }

    @PostMapping("/entries/{entryId}/reverse")
    public LedgerEntry reverse(@PathVariable Long entryId,
                               @RequestBody(required = false) ReverseRequest request) {
        String reason = request != null && request.reason() != null ? request.reason() : "manual correction";
        return ledgerService.reverseEntry(entryId, reason);
    }

    @GetMapping("/entries")
    public List<LedgerEntry> entries(@RequestParam Long userId) {
        return ledgerRepository.findByUserIdOrderByIdAsc(userId);
    }
}
