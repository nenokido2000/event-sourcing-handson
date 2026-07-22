plugins {
    `java-library`
}

// プロジェクション・読みモデル・クエリハンドラ（読み側）。読みモデルは PostgreSQL。
dependencies {
    api(project(":warehouse-domain"))
    implementation(platform(libs.axon.bom))
    implementation(libs.axon.messaging)

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
