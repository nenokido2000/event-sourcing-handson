package com.example.warehouse.app;

import com.example.warehouse.inventory.InventoryFrozenException;
import com.example.warehouse.receiving.InvalidClosureReasonException;
import com.example.warehouse.receiving.PutAwayExceedsRemainingException;
import com.example.warehouse.receiving.ReceiptAlreadyClosedException;
import com.example.warehouse.shared.InvalidQuantityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ドメインの拒否を RFC 9457 の {@code ProblemDetail} に載せる（docs/decisions.md H38）。
 *
 * <p><b>拡張フィールド {@code code} に例外の単純名を出す。</b>受入 Spec が例外名を名指ししているので、
 * 照合先が要る（specs/warehouse/*.spec の「直前の要求は … で拒否される」）。
 */
@RestControllerAdvice
public class ApiErrorHandler {

    /** 不変条件の違反・受付ゲートによる拒否。要求そのものが通らないので 409。 */
    @ExceptionHandler({
            PutAwayExceedsRemainingException.class,
            ReceiptAlreadyClosedException.class,
            InvalidClosureReasonException.class,
            InventoryFrozenException.class,
    })
    public ProblemDetail onRejected(RuntimeException rejection) {
        return problem(HttpStatus.CONFLICT, rejection);
    }

    /** 要求の組み立てがそもそもおかしい（数量ゼロなど）。 */
    @ExceptionHandler({InvalidQuantityException.class, IllegalArgumentException.class})
    public ProblemDetail onInvalidRequest(RuntimeException invalid) {
        return problem(HttpStatus.BAD_REQUEST, invalid);
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException failure) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, failure.getMessage());
        detail.setProperty("code", failure.getClass().getSimpleName());
        return detail;
    }
}
