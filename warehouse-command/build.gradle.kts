plugins {
    `java-library`
}

// コマンドハンドラ・Axon 設定（書き込み側）。
dependencies {
    api(project(":warehouse-domain"))
    implementation(platform(libs.axon.bom))
    implementation(libs.axon.messaging)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.axon.bom))
    testImplementation(libs.axon.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
