# docs/ の歩き方

この倉庫PoCの分析・設計成果物の入口。**目的別に読む道が3本**ある。全部を順番に読む必要はない。

## まず: 文書は3層に分かれている

| 層 | 何が書いてあるか | 文書 |
|---|---|---|
| **① 決定（正）** | 何を決めたか。仕様として引く | [`tactical-design.md`](tactical-design.md)（設計仕様）/ [`ubiquitous-language.md`](ubiquitous-language.md)（用語の正） |
| **② 決定の理由（ADR）** | 文脈・却下した選択肢・帰結。**決定と不可分** | [`decisions.md`](decisions.md) ← **決定の正はここ** |
| **③ 導出の物語（教材）** | 「なぜこの順に考えるとこう見えてくるか」という方法論 | [`event-storming/`](event-storming/) |

**①と②は切り離せない**（決定は理由とセットでしか検証できない）。切り離せるのは**②と③**で、
きちんと書かれた ADR は物語なしで自己完結する。読者が違うだけ。

---

## 道1: 全体を掴みたい（15分）

1. [`../README.md`](../README.md) … 何を作っているか・進捗
2. [`context-map.md`](context-map.md) … BC が4つ＋外部2つ。どこがコアか（**在庫＝在庫引当**）
3. [`decisions.md`](decisions.md) の**一覧表だけ** … 何が決まって何が未決か

これで「4集約・6ポリシー・コアは在庫引当」という骨格が掴めます
（ポリシーは M1 時点で P1〜P5、M2 の出荷スライスで P6 が加わった → [H17](decisions.md#h17-宙に浮いた引当を誰が解放するか)）。

## 道2: 決定を検証・再考したい（ADR）

→ [`decisions.md`](decisions.md) を読む。**これだけで足ります**（物語は不要）。

各決定に「検討した選択肢と却下理由」があるので、**なぜその案を採らなかったか**が分かります。
暫定・保留（[H5](decisions.md#h5-ロケーション間の在庫移動) / [H11](decisions.md#h11-棚卸中の引当をどこまで許すか)）は
見直し前提なので、蒸し返す前にここを見てください。

## 道3: イベントストーミングの進め方を学びたい（教材）

→ [`event-storming/`](event-storming/) を番号順に。**この PoC の主目的はここ**です。

| 文書 | 内容 |
|---|---|
| [`00-method.md`](event-storming/00-method.md) | **最初に読む**。3層の分け方・付箋の色・文法、そして**導出の物語**（集約がどう浮かんだか） |
| [`01-big-picture.md`](event-storming/01-big-picture.md) | ドメインイベントの時系列。まだ集約もポリシーも貼らない |
| [`02-process.md`](event-storming/02-process.md) | 各イベントに コマンド・アクター・ポリシー・リードモデルを紐付ける |
| [`03-software-design.md`](event-storming/03-software-design.md) | 集約・BC・コアサブドメインの確定 |

特に `00-method.md` の**導出の物語①〜④**が、この分析の中身です
（イベント1つから集約2つが割れた話／棚卸が差異を知らない理由／Saga が1本だけな理由）。

## 道4: 実装する（M3〜）

1. [`../specs/`](../specs/) … **受入基準（ATDD の外側ループ）**。「何ができていれば完成か」。実装はここを Red にして始める
2. [`tactical-design.md`](tactical-design.md) … 集約の状態・受付ゲート・例外・テスト骨子。**実装はこれを仕様として TDD で駆動する**
3. [`ubiquitous-language.md`](ubiquitous-language.md) … 命名で迷ったらここ（用語の正）
4. [`../.claude/rules/`](../.claude/rules/) … 遵守ルール（ES / 集約設計 / CQRS / 命名 / テスト）

外側（受入 Spec）と内側（Fixture テスト）の**役割分担は
[H31](decisions.md#h31-受入シナリオの置き場と粒度)** が正。同じ検証を両方に書かない。

判断に詰まったら [`decisions.md`](decisions.md) で「その論点が既に決着済みでないか」を確認してください。

---

## その他の文書

| 文書 | 用途 |
|---|---|
| [`plan.md`](plan.md) | **実行計画**（M0〜M8・技術選定・ガード整備）。設計の入口ではない |
| [`setup.md`](setup.md) | 環境構築の手順。clone した人が最初に読む |

## 書き足すときの約束

- **決定を書く場所は [`decisions.md`](decisions.md) だけ**。他の文書には結果を書き、理由はリンクする（重複を作らない）。
- 却下案は消さない（同じ議論を蒸し返さないための一次資料）。
- 表記規約（日本語主・英名はコード識別子・図の付箋色）は
  [`event-storming/00-method.md`](event-storming/00-method.md) の「表記の約束」「付箋の色」が正。
