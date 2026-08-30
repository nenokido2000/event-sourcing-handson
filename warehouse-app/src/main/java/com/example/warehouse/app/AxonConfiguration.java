package com.example.warehouse.app;

import com.example.warehouse.inventory.InventoryItem;
import com.example.warehouse.receiving.InboundReceipt;
import org.axonframework.config.AggregateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon の配線。
 *
 * <p><b>集約の登録をここで行うのは、{@code @Aggregate} が Spring のステレオタイプだから。</b>
 * {@code warehouse-domain} は Spring に依存しない（CLAUDE.md のモジュール構成 / docs/decisions.md H33）ので、
 * 注釈で登録できない。フレームワークへの結線はアプリ層の責務として集める。
 */
@Configuration
public class AxonConfiguration {

    @Bean
    public AggregateConfigurer<InboundReceipt> inboundReceiptConfigurer() {
        return AggregateConfigurer.defaultConfiguration(InboundReceipt.class);
    }

    @Bean
    public AggregateConfigurer<InventoryItem> inventoryItemConfigurer() {
        return AggregateConfigurer.defaultConfiguration(InventoryItem.class);
    }
}
