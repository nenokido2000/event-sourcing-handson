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
}

// Gauge CLI を直接起動する。H37 では `org.gauge` プラグインを借りる決定だったが、
// Gauge 1.6.35 は spec ディレクトリをプロジェクト配下に限定しており（`..` を含む位置引数は
// 切り詰められ、ディレクトリのシンボリックリンクも辿らない）、リポジトリルートの specs/ を指せなかった。
// プラグインの実体は「gauge を起動して gauge_custom_classpath を渡す」薄いラッパーなので、
// H37 の却下案どおり自前の Exec に落とす。spec の置き場は環境変数 gauge_specs_dir なら効く（実測）。
fun Exec.gaugeCommand(gaugeSubcommand: String) {
    group = "verification"
    dependsOn(tasks.testClasses)

    val stepClasspath = sourceSets["test"].runtimeClasspath
    // Gauge はディレクトリ配下の .spec / .md をすべて Spec として解析するため、
    // 案内文書（specs/README.md）と同じ階層を指すと ParseError になる。題材ごとの
    // サブディレクトリを指し、README は specs/ 直下に残す（docs/decisions.md H37 の改訂）。
    val specs = rootProject.layout.projectDirectory.dir("specs/warehouse").asFile

    inputs.files(stepClasspath)
    inputs.dir(specs)
    workingDir = projectDir
    commandLine(buildList {
        add("gauge")
        add(gaugeSubcommand)
        add("--env")
        add("default")
        // 実行するシナリオを絞る: ./gradlew :warehouse-atdd:gauge -Ptags=harness
        (project.findProperty("tags") as String?)?.let { add("--tags"); add(it) }
    })
    doFirst {
        // ステップ実装は test ソースセット。Gradle が組んだクラスパスを Gauge へ渡す
        environment("gauge_custom_classpath", stepClasspath.asPath)
        environment("gauge_specs_dir", specs.absolutePath)
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
