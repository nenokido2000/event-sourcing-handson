package com.example.warehouse.atdd;

import com.thoughtworks.gauge.datastore.ScenarioDataStore;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spec に書かれた識別子を、シナリオごとに一意な物理IDへ写す（docs/decisions.md H51）。
 *
 * <p>受入 Spec は起動しっぱなしのアプリに当てる（H37）。Spec の文面はシナリオをまたいで同じ
 * {@code RCP-1} / {@code SKU-A} / {@code A-01} を使い回すので、そのまま送ると2本目が1本目のデータに
 * ぶつかる。同じスイートを2回流したときも同じことが起きる。
 *
 * <p><b>写すのは識別子だけ</b>。数量・理由・完了区分・状態・例外名・コマンド名は Spec の値のまま送る。
 * <b>応答から得た識別子は写さない</b>——すでに物理IDなので、二重に付けると壊れる。
 *
 * <p><b>ステップ実装は識別子の引数を必ずここへ通すこと。</b>通し忘れると隔離が漏れるが、
 * 前のシナリオのデータと衝突して期待値が合わなくなるため、<b>緑のまま見逃されるのではなく赤として出る</b>。
 */
final class ScenarioIds {

    /** 在庫ID・引当IDの複合を組み立てる分離子。{@code Sku} / {@code LocationId} には現れない（tactical-design.md） */
    private static final String COMPOUND_SEPARATOR = "@";

    private static final String KEY = "シナリオの接尾辞";

    private ScenarioIds() {
    }

    /** 単一の識別子（入荷ID・SKU・ロケーションID・受注ID・注文明細ID・出荷ID・棚卸ID）を写す。 */
    static String of(String logicalId) {
        return logicalId + "-" + suffix();
    }

    /**
     * 複合の識別子を写す。引当ID {@code OL-1@A-01} は {@code OrderLineId} × {@code LocationId} を
     * 決定的に導出したもの（tactical-design.md の識別子表）なので、<b>両側を写す</b>。
     */
    static String ofCompound(String logicalId) {
        String[] parts = logicalId.split(COMPOUND_SEPARATOR, -1);
        if (parts.length != 2) {
            throw new AssertionError(
                    "複合の識別子は \"左" + COMPOUND_SEPARATOR + "右\" の形で書いてください: " + logicalId);
        }
        return of(parts[0]) + COMPOUND_SEPARATOR + of(parts[1]);
    }

    /**
     * シナリオ1本ぶんの接尾辞。{@link ScenarioDataStore} に置くので<b>シナリオ境界でランナーが捨てる</b>。
     * ランダム由来なので、同じスイートを2回流しても衝突しない。
     */
    private static String suffix() {
        Object remembered = ScenarioDataStore.get(KEY);
        if (remembered != null) {
            return (String) remembered;
        }
        // 分離子 "@" を含まない文字だけで作る（Sku / LocationId の検証を壊さないため）
        String created = Long.toString(ThreadLocalRandom.current().nextLong(1L << 32), 36);
        ScenarioDataStore.put(KEY, created);
        return created;
    }
}
