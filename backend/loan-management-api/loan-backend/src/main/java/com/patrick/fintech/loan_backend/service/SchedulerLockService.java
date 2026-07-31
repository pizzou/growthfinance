package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Cross-instance mutual exclusion for {@link ScheduledJobs}, using a plain DB table instead of
 * an external library (e.g. ShedLock) so it needs no new dependency and works identically on
 * H2 (dev) and Postgres (prod). Not a general-purpose distributed lock — it's specifically
 * sized for once-a-day/once-an-hour cron jobs, where a few milliseconds of lock-acquisition
 * overhead is irrelevant and jobs are expected to run to completion well within lockFor.
 *
 * How it works: try to INSERT a row for this job name. If another instance already holds an
 * unexpired lock, the INSERT hits the primary key and fails — that failure IS the "someone else
 * has it" signal. If the existing lock has expired (the previous holder crashed mid-job without
 * releasing), a conditional UPDATE steals it.
 */
@Service
@Slf4j
public class SchedulerLockService {

    @PersistenceContext
    private EntityManager em;

    /** Attempts to acquire the named lock for up to {@code lockFor}. Returns true if this call
     *  now holds it (the caller must run its job and then call {@link #release}), false if
     *  another instance currently holds it. Each call runs in its own transaction so a failed
     *  INSERT (duplicate key) doesn't poison whatever transaction the caller is in. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String jobName, Duration lockFor) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plus(lockFor);
        try {
            em.createNativeQuery("INSERT INTO scheduler_locks (job_name, locked_until) VALUES (?1, ?2)")
                .setParameter(1, jobName).setParameter(2, until).executeUpdate();
            return true;
        } catch (DataIntegrityViolationException alreadyLocked) {
            int stolen = em.createNativeQuery(
                    "UPDATE scheduler_locks SET locked_until = ?1 WHERE job_name = ?2 AND locked_until < ?3")
                .setParameter(1, until).setParameter(2, jobName).setParameter(3, now)
                .executeUpdate();
            return stolen == 1;
        }
    }

    /** Releases the lock early (job finished before lockFor elapsed) so the next scheduled run
     *  on any instance doesn't have to wait out the full lock window. Safe to call even if this
     *  instance no longer actually holds it (e.g. it expired and was stolen) — the WHERE clause
     *  on job_name alone means a stray release just resets locked_until to "now", which is
     *  functionally the same as it already being expired. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String jobName) {
        em.createNativeQuery("UPDATE scheduler_locks SET locked_until = ?1 WHERE job_name = ?2")
            .setParameter(1, LocalDateTime.now()).setParameter(2, jobName).executeUpdate();
    }

    /** Runs {@code job} only if the lock is acquired; logs and skips otherwise. Always releases
     *  on the way out, including when {@code job} throws. */
    public void runExclusively(String jobName, Duration lockFor, Runnable job) {
        if (!tryAcquire(jobName, lockFor)) {
            log.info("[Scheduler] '{}' is already running on another instance — skipping", jobName);
            return;
        }
        try {
            job.run();
        } finally {
            release(jobName);
        }
    }
}
