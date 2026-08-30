package com.example.warehouse.receiving;

/** クローズ済みの入荷への格納・再クローズ。終わった入荷は動かない。 */
public class ReceiptAlreadyClosedException extends RuntimeException {

    public ReceiptAlreadyClosedException(String message) {
        super(message);
    }
}
