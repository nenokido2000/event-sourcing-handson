plugins {
    java
    alias(libs.plugins.gauge)
}

// 受入テスト（ATDD）。Spec の文面はリポジトリルートの specs/ が正で、ここにはステップ実装だけを置く。
// 組み込み方の理由は docs/decisions.md H37。
//
// ステップ実装を src/test/java に置くのは、プラグイン 3.2.0 が test ソースセットの runtimeClasspath を
// gauge_custom_classpath として Gauge CLI へ渡す実装だから（AbstractGaugeTask を確認済み）。
dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))

    testImplementation(libs.gauge.java)
    testImplementation(libs.playwright)
    testImplementation(libs.assertj.core)
}

gauge {
    // Spec は動かさない。プラグイン側をルートの specs/ に向ける
    specsDir.set("../specs")
    env.set("default")
}

// check / test には繋がない。アプリの手動起動が前提なので、明示的に呼ぶときだけ動く（H37）
tasks.named("test") {
    enabled = false
}
