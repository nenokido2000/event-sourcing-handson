plugins {
    java
    alias(libs.plugins.spring.boot)
}

// Spring Boot 起動・REST API。書き側/読み側モジュールを束ねる。
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.axon.bom))

    implementation(project(":warehouse-command"))
    implementation(project(":warehouse-query"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.axon.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
