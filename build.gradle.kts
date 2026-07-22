// ルート集約プロジェクト。共通設定のみを持ち、成果物は各サブモジュールが生成する。
allprojects {
    group = "com.example.warehouse"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }

    // java / java-library が適用されたモジュールに共通の Java 設定を反映する
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
