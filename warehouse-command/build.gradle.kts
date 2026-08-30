plugins {
    `java-library`
}

// ポリシー・サーガ（書き込み側）。リードモデルは QueryGateway で読む（query へコンパイル依存しない）。
dependencies {
    api(project(":warehouse-domain"))
    implementation(platform(libs.axon.bom))
    implementation(libs.axon.messaging)
    implementation(libs.axon.configuration)

    // ポリシーは @Component の Spring Bean（docs/tactical-design.md ポリシーの共通の形）。
    // warehouse-domain と違い、このモジュールは Spring に依存してよい（CLAUDE.md のモジュール構成）
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.context)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.axon.bom))
    testImplementation(libs.axon.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.hamcrest)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
