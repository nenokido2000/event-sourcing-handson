plugins {
    `java-library`
}

// 純ドメイン（集約・コマンド・イベント）。Spring には依存しない。
dependencies {
    api(platform(libs.axon.bom))
    api(libs.axon.modelling)
    // @EventSourcingHandler / AggregateLifecycle。集約の登録は Spring 依存なので warehouse-app が行う
    api(libs.axon.eventsourcing)

    // 値オブジェクトを平坦化してイベントの永続化形式を固定する（docs/decisions.md H52）。
    // 入るのは注釈だけの jar で、databind は入らない。版を Spring Boot の BOM から取るのは
    // アプリが解決する版とズレないようにするためで、BOM は制約のみ＝Spring のクラスは入らない
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.jackson.annotations)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.axon.bom))
    testImplementation(libs.axon.test)
    // イベントのJSONを固定するテスト用。axon-messaging は Jackson を推移的に持たない（H35）
    testImplementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.junit.platform.launcher)
}
