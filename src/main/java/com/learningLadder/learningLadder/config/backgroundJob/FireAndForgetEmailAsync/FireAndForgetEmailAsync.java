package com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class FireAndForgetEmailAsync {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 2_000;

    private final ThreadPoolExecutor executor;

    public FireAndForgetEmailAsync(
            @Value("${app.email.async.pool-size:4}") int poolSize,
            @Value("${app.email.async.queue-capacity:1000}") int queueCapacity) {

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "email-async-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };

        this.executor = new ThreadPoolExecutor(
                poolSize, poolSize,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public void fire(String description, Runnable emailTask) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(description, emailTask);
                }
            });
        } else {
            submit(description, emailTask);
        }
    }

    private void submit(String description, Runnable emailTask) {
        try {
            executor.execute(() -> runWithRetry(description, emailTask));
        } catch (RejectedExecutionException ex) {
            log.error("Email queue is full, email dropped [{}]", description);
        }
    }

    private void runWithRetry(String description, Runnable emailTask) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                emailTask.run();
                log.debug("Email sent [{}] on attempt {}", description, attempt);
                return;
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Email failed permanently [{}] after {} attempts: {}",
                            description, MAX_ATTEMPTS, ex.getMessage());
                    return;
                }
                long delayMs = BASE_BACKOFF_MS * (1L << (attempt - 1));
                log.warn("Email attempt {}/{} failed [{}]: {}. Retrying in {} ms",
                        attempt, MAX_ATTEMPTS, description, ex.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Email retry interrupted during shutdown [{}]", description);
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                int pending = executor.shutdownNow().size();
                log.warn("Email executor stopped with {} email(s) still queued", pending);
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}