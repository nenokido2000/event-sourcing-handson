# ユビキタス言語 / 命名

- コマンドは命令形の動詞で命名する（例: `ReceiveStock`, `AllocateStock`, `ShipStock`）。
- イベントは過去形で命名する（例: `StockReceived`, `StockAllocated`, `StockShipped`）。
- 集約・値オブジェクトは名詞で命名する（例: `InventoryItem`, `Sku`, `Quantity`）。数量・識別子などは値オブジェクトにして原始型の乱用を避ける。
- ドメインの言葉をコードにそのまま使う。技術用語（DTO/Manager/Impl 等）でドメイン概念を置き換えない。
- パッケージは境界づけられたコンテキスト（BC）単位で切る（例: `inventory`, `receiving`, `fulfillment`, `stocktaking`）。BCをまたぐ直接依存を作らない。
- 用語集は `docs/` のユビキタス言語表を正とし、コード・ドキュメント・会話で表記を揃える。
