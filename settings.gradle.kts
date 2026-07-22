rootProject.name = "event-sourcing"

// warehouse-eventstore-dynamodb は M4 で追加する
include(
    "warehouse-domain",
    "warehouse-command",
    "warehouse-query",
    "warehouse-app",
)
