package com.example.warehouse.atdd;

import java.time.Duration;

/**
 * 結果整合の待ち。コマンドが返っても投影はまだ追いついていないので、期待は「いつか成り立つ」形で書く。
 *
 * <p><b>待ち時間を Spec に書かない</b>のが約束（docs/decisions.md H31・H38）。Spec は業務の言葉だけを持ち、
 * どれだけ待つかはここに閉じる。既定5秒・環境変数 {@code WAREHOUSE_PROJECTION_TIMEOUT_MS} で調整する。
 */
final class EventualConsistency {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private EventualConsistency() {
    }

    /**
     * {@code assertion} が通るまで繰り返す。時間切れなら最後の失敗をそのまま投げる——
     * 「時間切れ」ではなく「何がどう違ったか」が見えないと調査にならない。
     */
    static void awaitAssertion(String what, Runnable assertion) {
        long deadline = System.nanoTime() + timeout().toNanos();
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError failure) {
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError(
                            what + " が " + timeout().toMillis() + "ms 以内に成立しませんでした", failure);
                }
            }
            sleep();
        }
    }

    private static Duration timeout() {
        String configured = System.getenv("WAREHOUSE_PROJECTION_TIMEOUT_MS");
        return configured == null || configured.isBlank()
                ? Duration.ofSeconds(5)
                : Duration.ofMillis(Long.parseLong(configured));
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("結果整合の待ちが中断されました", interrupted);
        }
    }
}
