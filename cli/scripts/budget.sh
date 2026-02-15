if [ "$1" = 'debug' ]; then
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1044 \
      -cp "${BPS_BUDGET_CLI_DEBUG_CLASSPATH:-./cli/build/libs}/cli-1.0-SNAPSHOT-all.jar" \
      bps.budget.Budget
elif [ "$1" = 'test' ]; then
  java \
      -cp "${BPS_BUDGET_CLI_DEBUG_CLASSPATH:-./cli/build/libs}/cli-1.0-SNAPSHOT-all.jar" \
      bps.budget.Budget
else
  java \
      -cp "${BPS_BUDGET_CLI_CLASSPATH:-${HOME}/.local/share/bps-budget/libs}/cli-1.0-SNAPSHOT-all.jar" \
      bps.budget.Budget
fi
