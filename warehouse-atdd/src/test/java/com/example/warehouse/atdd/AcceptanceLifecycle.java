package com.example.warehouse.atdd;

import com.thoughtworks.gauge.AfterSuite;
import com.thoughtworks.gauge.BeforeSuite;

/**
 * 受入スイートの前後。HTTP クライアントはスイートで1つだけ作って使い回す。
 *
 * <p>アプリの起動はここでは行わない——別端末で手動起動しておく前提（docs/decisions.md H37）。
 */
public class AcceptanceLifecycle {

    @BeforeSuite
    public void openHttpClient() {
        AcceptanceHttpClient.open();
    }

    @AfterSuite
    public void closeHttpClient() {
        AcceptanceHttpClient.close();
    }
}
