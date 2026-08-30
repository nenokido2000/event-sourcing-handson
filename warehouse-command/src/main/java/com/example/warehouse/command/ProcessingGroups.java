package com.example.warehouse.command;

/**
 * イベント処理グループの名前。
 *
 * <p><b>ポリシーはプロジェクションと処理グループを分ける</b>（docs/decisions.md H30）。
 * リードモデルは使い捨てで再構築できるが、<b>ポリシーを巻き戻すと過去のコマンドが全部再発行される</b>。
 * 同居させるとリセット操作ひとつで在庫が壊れるので、
 * <b>再構築してよいものと、してはいけないものを構造で分けておく。</b>
 */
public final class ProcessingGroups {

    /** 再構築してはいけない処理。ポリシー7本と、棚卸干渉ビューの更新が属する（H48） */
    public static final String POLICY = "policy";

    /** 再構築してよいリードモデルの投影 */
    public static final String PROJECTION = "projection";

    private ProcessingGroups() {
    }
}
