package com.example.warehouse.atdd;

import com.thoughtworks.gauge.AfterScenario;
import com.thoughtworks.gauge.ExecutionContext;
import com.thoughtworks.gauge.Step;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * コマンドが拒否されたことへの期待。receiving / allocation / stocktaking が共有する（docs/decisions.md H50）。
 *
 * <p>不変条件の違反は<b>コマンドの同期応答</b>で返るので、投影の結果整合を待たない
 * （{@link EventualConsistency} を通さないのはこのため）。
 */
public class CommandRejectionSteps {

    @Step("直前の要求は <exceptionName> で拒否される")
    public void lastCommandWasRejectedWith(String exceptionName) {
        RejectedCommand rejection = RejectedCommand.consume();

        assertThat(rejection.code())
                .as("%s の拒否理由（HTTP %d）", rejection.what(), rejection.status())
                .isEqualTo(exceptionName);
    }

    /**
     * 拒否したまま誰も照合せずにシナリオが終わるのを防ぐ。すでに失敗しているシナリオでは何もしない——
     * 本当の失敗原因を、この後始末の失敗で覆い隠さないため。
     */
    @AfterScenario
    public void rejectionMustBeAsserted(ExecutionContext context) {
        if (Boolean.TRUE.equals(context.getCurrentScenario().getIsFailing())) {
            return;
        }
        RejectedCommand.failIfUnconsumed();
    }
}
