plugins {
    `java-library`
}

// 純ドメイン（集約・コマンド・イベント）。Spring には依存しない。
dependencies {
    api(platform(libs.axon.bom))
    api(libs.axon.modelling)
    // @EventSourcingHandler / AggregateLifecycle。集約の登録は Spring 依存なので warehouse-app が行う
    api(libs.axon.eventsourcing)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.axon.bom))
    testImplementation(libs.axon.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.junit.platform.launcher)
}
