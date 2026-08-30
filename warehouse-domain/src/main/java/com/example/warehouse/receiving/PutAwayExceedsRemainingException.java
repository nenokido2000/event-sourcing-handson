package com.example.warehouse.receiving;

/** 格納量 > 残格納量。入荷の不変条件（格納累計 ≤ 受入量）の違反。 */
public class PutAwayExceedsRemainingException extends RuntimeException {

    public PutAwayExceedsRemainingException(String message) {
        super(message);
    }
}
