/**
 * 共有カーネル。どの BC でも同じ意味を持つ値オブジェクトだけを置く
 * （docs/decisions.md H43 / docs/context-map.md の共有カーネル）。
 *
 * <p>ここに BC 固有の識別子（{@code ReceiptId} 等）を置かない。置くと BC 間が値で結合する。
 */
package com.example.warehouse.shared;
