plugins {
    java
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
    testImplementation(libs.gson)
}

// Gauge CLI を直接起動する（`org.gauge` プラグインは使わない / H37 の改訂）。
//
// Gauge プロジェクト（manifest.json / env/）はリポジトリルートに置く。ルートを起点にすれば
// specs/ がプロジェクト内に収まり、位置引数がそのまま効く。渡すのは Gradle が組んだクラスパスだけ。
fun Exec.gaugeCommand(gaugeSubcommand: String) {
    group = "verification"
    dependsOn(tasks.testClasses)

    val stepClasspath = sourceSets["test"].runtimeClasspath
    // Gauge はディレクトリ配下の .spec / .md をすべて Spec として解析するため、
    // 案内文書（specs/README.md）と同じ階層を指すと ParseError になる。題材ごとの
    // サブディレクトリを指し、README は specs/ 直下に残す。
    val specsDir = "specs/warehouse"

    inputs.files(stepClasspath)
    inputs.dir(rootProject.layout.projectDirectory.dir(specsDir))
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine(buildList {
        add("gauge")
        add(gaugeSubcommand)
        add("--env")
        add("default")
        // 実行するシナリオを絞る: ./gradlew :warehouse-atdd:gauge -Ptags=harness
        (project.findProperty("tags") as String?)?.let { add("--tags"); add(it) }
        add(specsDir)
    })
    doFirst {
        // ステップ実装は test ソースセット。Gradle が組んだクラスパスを Gauge へ渡す
        environment("gauge_custom_classpath", stepClasspath.asPath)
    }
}

// check / test には繋がない。アプリの手動起動が前提なので、明示的に呼ぶときだけ動く（H37）
tasks.register<Exec>("gauge") {
    description = "受入 Spec（specs/）を実行する。アプリを別端末で起動しておくこと"
    gaugeCommand("run")
}

tasks.register<Exec>("gaugeValidate") {
    description = "受入 Spec の構文とステップ実装の有無を検証する（アプリ起動は不要）"
    gaugeCommand("validate")
}

tasks.named("test") {
    enabled = false
}
