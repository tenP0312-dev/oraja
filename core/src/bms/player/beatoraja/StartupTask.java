package bms.player.beatoraja;

final class StartupTask {
    enum Outcome {
        OK,
        SKIP
    }

    record Result(Outcome outcome, String detail) {
        static Result ok() {
            return new Result(Outcome.OK, "");
        }

        static Result ok(String detail) {
            return new Result(Outcome.OK, detail == null ? "" : detail);
        }

        static Result skip(String reason) {
            return new Result(Outcome.SKIP, reason == null ? "" : reason);
        }
    }

    @FunctionalInterface
    interface Operation {
        Result run() throws Exception;
    }

    final String label;
    final boolean fatal;
    final Operation operation;

    StartupTask(String label, boolean fatal, Operation operation) {
        this.label = label;
        this.fatal = fatal;
        this.operation = operation;
    }

    static StartupTask required(String label, Operation operation) {
        return new StartupTask(label, true, operation);
    }

    static StartupTask optional(String label, Operation operation) {
        return new StartupTask(label, false, operation);
    }
}
