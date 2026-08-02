package com.patrick.fintech.loan_backend.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RateLimiter {

    private static class Window {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStartMillis = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;

    public RateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    
    public boolean tryAcquire(String key) {
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (now - w.windowStartMillis > windowMillis) {
                w.windowStartMillis = now;
                w.count.set(0);
            }
            return w.count.incrementAndGet() <= maxRequests;
        }
    }

    /** How many seconds until this key's window resets — for a Retry-After header. */
    public long secondsUntilReset(String key) {
        Window w = windows.get(key);
        if (w == null) return 0;
        long elapsed = System.currentTimeMillis() - w.windowStartMillis;
        long remaining = windowMillis - elapsed;
        return Math.max(0, remaining / 1000);
    }

   
    void evictStale() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().windowStartMillis > windowMillis * 4);
    }
}