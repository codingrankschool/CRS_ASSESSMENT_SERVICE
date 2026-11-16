package com.codingrank.assessment.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many test cases of a running submission are done, so the page can show "7 / 20" while
 * the student waits. Kept in memory only and forgotten after a few minutes.
 */
@Component
public class JudgeProgressRegistry {

    private static final long KEEP_MS = 5 * 60 * 1000L;

    public record Progress(int done, int total, String phase) {}

    private static final class Entry {
        final AtomicInteger done = new AtomicInteger();
        volatile int total;
        volatile String phase = "queued";
        volatile String owner;
        volatile long touchedAt = System.currentTimeMillis();
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Ids come from the page, so only short, plain ones are accepted. */
    public static boolean isValidId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{8,64}");
    }

    public void start(String id, String owner, int total) {
        if (!isValidId(id)) return;
        purgeOld();
        Entry entry = new Entry();
        entry.total = total;
        entry.owner = owner;
        entry.phase = "running";
        entries.put(id, entry);
    }

    public void phase(String id, String phase) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry != null) {
            entry.phase = phase;
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    public void caseDone(String id) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry != null) {
            entry.done.incrementAndGet();
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    public void finish(String id) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry != null) {
            entry.done.set(entry.total);
            entry.phase = "done";
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    /** Only the student who submitted can read it. */
    public Optional<Progress> read(String id, String requester) {
        Entry entry = id == null ? null : entries.get(id);
        if (entry == null) return Optional.empty();
        if (entry.owner != null && requester != null && !entry.owner.equals(requester)) return Optional.empty();
        return Optional.of(new Progress(Math.min(entry.done.get(), entry.total), entry.total, entry.phase));
    }

    private void purgeOld() {
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        entries.entrySet().removeIf(e -> e.getValue().touchedAt < cutoff);
    }
}
