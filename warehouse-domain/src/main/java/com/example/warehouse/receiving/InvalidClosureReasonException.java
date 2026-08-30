package com.example.warehouse.receiving;

/** {@code COMPLETED} を指定した打ち切り要求。全量格納は集約が自動で発行する。 */
public class InvalidClosureReasonException extends RuntimeException {

    public InvalidClosureReasonException(String message) {
        super(message);
    }
}
