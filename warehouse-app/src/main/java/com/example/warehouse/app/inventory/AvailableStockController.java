package com.example.warehouse.app.inventory;

import com.example.warehouse.query.inventory.AvailableStock;
import com.example.warehouse.query.inventory.FindAvailableStock;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 引当可能在庫ビューの照会（docs/decisions.md H38）。
 *
 * <p><b>URL にビューの実装名を出さない。</b>リードモデルは使い捨て可能・再構築可能なので、
 * URL が指すのは「引当可能在庫」という業務概念で、その裏に {@code AvailableStockView} があるだけ。
 */
@RestController
public class AvailableStockController {

    private final QueryGateway queryGateway;

    public AvailableStockController(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @GetMapping("/api/available-stock")
    public List<AvailableStock> find(@RequestParam String sku,
                                     @RequestParam(required = false) String location) {
        try {
            return queryGateway.query(
                    new FindAvailableStock(sku, location),
                    ResponseTypes.multipleInstancesOf(AvailableStock.class)).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("引当可能在庫の照会が中断されました", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("引当可能在庫の照会に失敗しました", failure.getCause());
        }
    }
}
