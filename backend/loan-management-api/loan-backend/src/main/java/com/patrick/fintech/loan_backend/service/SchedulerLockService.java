package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SchedulerLockService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Attempts to acquire a distributed scheduler lock.
     *
     * PostgreSQL performs this atomically:
     *
     * 1. If the job does not exist -> INSERT the lock.
     * 2. If the job exists but has expired -> UPDATE the lock.
     * 3. If the job exists and is still locked -> do nothing.
     *
     * This avoids catching a duplicate-key exception from an INSERT,
     * which can otherwise poison the current database transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(
            String jobName,
            Duration lockFor) {

        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException(
                    "Scheduler job name is required"
            );
        }

        if (lockFor == null || lockFor.isNegative()
                || lockFor.isZero()) {

            throw new IllegalArgumentException(
                    "Scheduler lock duration must be greater than zero"
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime lockedUntil =
                now.plus(lockFor);

        /*
         * PostgreSQL UPSERT.
         *
         * The important part is:
         *
         * WHERE scheduler_locks.locked_until < :now
         *
         * If the existing lock has not expired,
         * PostgreSQL performs no update.
         */
        String sql = """
                INSERT INTO scheduler_locks (
                    job_name,
                    locked_until
                )
                VALUES (
                    :jobName,
                    :lockedUntil
                )
                ON CONFLICT (job_name)
                DO UPDATE SET
                    locked_until = EXCLUDED.locked_until
                WHERE scheduler_locks.locked_until < :now
                """;

        int affected =
                em.createNativeQuery(sql)
                        .setParameter(
                                "jobName",
                                jobName
                        )
                        .setParameter(
                                "lockedUntil",
                                lockedUntil
                        )
                        .setParameter(
                                "now",
                                now
                        )
                        .executeUpdate();

        boolean acquired =
                affected == 1;

        if (acquired) {

            log.debug(
                    "[SchedulerLock] Acquired lock '{}' until {}",
                    jobName,
                    lockedUntil
            );

        } else {

            log.info(
                    "[SchedulerLock] Lock '{}' is already held by another instance — skipping",
                    jobName
            );
        }

        return acquired;
    }


    /**
     * Releases a scheduler lock immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(
            String jobName) {

        if (jobName == null || jobName.isBlank()) {
            return;
        }

        int affected =
                em.createNativeQuery("""
                        UPDATE scheduler_locks
                        SET locked_until = :now
                        WHERE job_name = :jobName
                        """)
                        .setParameter(
                                "now",
                                LocalDateTime.now()
                        )
                        .setParameter(
                                "jobName",
                                jobName
                        )
                        .executeUpdate();

        if (affected == 1) {

            log.debug(
                    "[SchedulerLock] Released lock '{}'",
                    jobName
            );

        } else {

            log.debug(
                    "[SchedulerLock] Lock '{}' did not exist during release",
                    jobName
            );
        }
    }


    /**
     * Convenience method for executing a job exclusively.
     */
    public void runExclusively(
            String jobName,
            Duration lockFor,
            Runnable job) {

        if (!tryAcquire(jobName, lockFor)) {

            log.info(
                    "[Scheduler] '{}' is already running on another instance — skipping",
                    jobName
            );

            return;
        }

        try {

            job.run();

        } finally {

            release(jobName);
        }
    }
}
