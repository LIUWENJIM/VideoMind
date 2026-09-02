package com.example.server.service;

import java.util.concurrent.TimeUnit;

/**
 * Carries the current Agent deadline through the synchronous orchestration thread.
 * Model calls use the remaining time as their own timeout, so a slow provider cannot
 * silently run past the task budget.
 */
public final class AgentExecutionBudget {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private AgentExecutionBudget() {
    }

    /**
     * 清除当前线程上残留的 deadline。
     *
     * <p>RocketMQ 消费者线程池会复用线程，如果上一个任务的 deadline 没有被正确清理，
     * 下一个任务会继承旧的 deadline，导致预算提前耗尽。在每次 Agent run() 入口调用此方法
     * 可以确保每次执行都有干净的预算状态。
     */
    public static void resetThreadLocal() {
        DEADLINE_NANOS.remove();
    }

    public static Scope open(long maxDurationMs) {
        if (maxDurationMs < 1) throw new IllegalArgumentException("Agent 执行时长预算必须大于 0");
        Long previous = DEADLINE_NANOS.get();
        long requested = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxDurationMs);
        DEADLINE_NANOS.set(previous == null ? requested : Math.min(previous, requested));
        return () -> {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        };
    }

    public static long remainingMillis() {
        Long deadline = DEADLINE_NANOS.get();
        if (deadline == null) return Long.MAX_VALUE;
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) throw new DeadlineExceededException("Agent 已耗尽执行时长预算");
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    public static void check(String stage) {
        try {
            remainingMillis();
        } catch (DeadlineExceededException e) {
            throw new DeadlineExceededException(stage + " 后终止：" + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static class DeadlineExceededException extends IllegalStateException {
        public DeadlineExceededException(String message) {
            super(message);
        }
    }
}
