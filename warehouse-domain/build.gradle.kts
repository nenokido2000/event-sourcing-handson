plugins {
    `java-library`
}

// 純ドメイン（集約・コマンド・イベント）。Spring には依存しない。
dependencies {
    api(platform(libs.axon.bom))
    api(libs.axon.modelling)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.axon.bom))
    testImplementation(libs.axon.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
