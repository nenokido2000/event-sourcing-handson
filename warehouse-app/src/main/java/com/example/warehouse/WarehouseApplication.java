package com.example.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 倉庫在庫管理 PoC のアプリケーション起動点。
 * コンポーネントスキャンの基底パッケージは {@code com.example.warehouse}（全モジュール共通）。
 */
@SpringBootApplication
public class WarehouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseApplication.class, args);
    }
}
