/**
 * 倉庫在庫管理の純ドメイン（集約・コマンド・イベント・値オブジェクト）。
 *
 * <p>境界づけられたコンテキストごとにサブパッケージを切る:
 * {@code inventory}（コア=在庫引当）/ {@code receiving} / {@code fulfillment} / {@code stocktaking}。
 * 集約・コマンド・イベントの追加は M2/M3 で行う。
 */
package com.example.warehouse;
