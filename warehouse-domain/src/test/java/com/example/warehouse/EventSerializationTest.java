package com.example.warehouse;

import com.example.warehouse.inventory.AllocationId;
import com.example.warehouse.inventory.InventoryItemId;
import com.example.warehouse.inventory.event.StockAllocated;
import com.example.warehouse.inventory.event.StockPlaced;
import com.example.warehouse.receiving.ClosureReason;
import com.example.warehouse.receiving.ReceiptId;
import com.example.warehouse.receiving.event.InboundReceiptClosed;
import com.example.warehouse.receiving.event.StockPutAway;
import com.example.warehouse.receiving.event.StockReceived;
import com.example.warehouse.shared.LocationId;
import com.example.warehouse.shared.OrderLineId;
import com.example.warehouse.shared.Quantity;
import com.example.warehouse.shared.Sku;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.jackson3.Jackson3Serializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * イベントの永続化形式。<b>イベントは不変で永久に読み続けるので、JSON の形をここで文字列として固定する</b>
 * （docs/decisions.md H35・H52）。
 *
 * <p>守りたいのは見た目ではなく<b>形の安定</b>。値オブジェクトに振る舞い（{@code isZero()} のような
 * {@code isXxx()} / {@code getXxx()}）を足すと Jackson がゲッターとして拾い、永続化形式が黙って変わる。
 * 値オブジェクトを {@code @JsonValue} で平坦化すると出力が1つに固定されるので、この事故が起きなくなる。
 * <b>このテストが、その規約を守らせる唯一の仕掛け</b>——新しい値オブジェクトを足したらここに行を足す。
 *
 * <p>複合の {@link InventoryItemId} だけはネストのまま残す（H52）。集約識別子の文字列表現
 * （{@code SKU-A@A-01}）に畳むと、分離子の規約がイベントに焼き込まれてアップキャスタが永久に知る必要がある。
 */
class EventSerializationTest {

    private final Serializer serializer = Jackson3Serializer.defaultSerializer();

    @Test
    void 入荷したイベントは平坦なJSONになる() {
        assertRoundTrip(
                new StockReceived(new ReceiptId("RCP-1"), new Sku("SKU-A"), new Quantity(50)),
                """
                {"receiptId":"RCP-1","sku":"SKU-A","quantity":50}""");
    }

    @Test
    void 棚へ格納したイベントは平坦なJSONになる() {
        assertRoundTrip(
                new StockPutAway(new ReceiptId("RCP-1"), new Sku("SKU-A"), new LocationId("A-01"),
                        new Quantity(20), new Quantity(50)),
                """
                {"receiptId":"RCP-1","sku":"SKU-A","locationId":"A-01","quantity":20,"putAwayTotal":50}""");
    }

    @Test
    void 在庫を計上したイベントは複合の在庫IDだけネストで残る() {
        assertRoundTrip(
                new StockPlaced(new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01")),
                        new Quantity(20), new com.example.warehouse.inventory.ReceiptId("RCP-1"),
                        new Quantity(50)),
                """
                {"inventoryItemId":{"sku":"SKU-A","locationId":"A-01"},"quantity":20,\
                "receiptId":"RCP-1","putAwayTotal":50}""");
    }

    @Test
    void 引き当てたイベントは複合IDを2つ持つ() {
        assertRoundTrip(
                new StockAllocated(new InventoryItemId(new Sku("SKU-A"), new LocationId("A-01")),
                        new AllocationId(new OrderLineId("OL-1"), new LocationId("A-01")),
                        new Quantity(30)),
                """
                {"inventoryItemId":{"sku":"SKU-A","locationId":"A-01"},\
                "allocationId":{"orderLineId":"OL-1","locationId":"A-01"},"quantity":30}""");
    }

    @Test
    void 入荷が終わったイベントは理由を列挙の名前で持つ() {
        assertRoundTrip(
                new InboundReceiptClosed(new ReceiptId("RCP-1"), Quantity.ZERO, ClosureReason.DAMAGED),
                """
                {"receiptId":"RCP-1","remainingQuantity":0,"reason":"DAMAGED"}""");
    }

    /**
     * 書き出した JSON が期待どおりで、かつ<b>そこから元のイベントへ戻せる</b>ことを見る。
     * 平坦化は書き出し側（{@code @JsonValue}）と読み込み側（{@code @JsonCreator}）が揃って初めて成立し、
     * 片方だけだと書けるのに読めないイベント——ES では最悪の壊れ方——になる。
     */
    private void assertRoundTrip(Object event, String expectedJson) {
        SerializedObject<String> serialized = serializer.serialize(event, String.class);

        assertThat(serialized.getData())
                .as("%s の永続化形式", event.getClass().getSimpleName())
                .isEqualTo(expectedJson);
        assertThat(serializer.<String, Object>deserialize(serialized))
                .as("%s を JSON から復元したもの", event.getClass().getSimpleName())
                .isEqualTo(event);
    }
}
