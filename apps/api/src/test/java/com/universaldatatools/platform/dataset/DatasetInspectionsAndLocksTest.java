package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.TableInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-04 task 10: the inspection cache and the per-dataset locks. */
class DatasetInspectionsAndLocksTest {

    private static final UUID ID = UUID.randomUUID();
    private static final TableInfo INFO = TableInfo.forRead(DataFormat.CSV,
            new ResolvedReadOptions(null, Delimiter.COMMA, null, true), List.of());

    @Test
    void an_inspection_is_reused_for_the_same_options_only() {
        DatasetInspections cache = new DatasetInspections(256);
        AtomicInteger inspections = new AtomicInteger();

        cache.get(ID, ReadOptions.defaults(), () -> count(inspections));
        cache.get(ID, ReadOptions.defaults(), () -> count(inspections));
        cache.get(ID, new ReadOptions(null, Delimiter.SEMICOLON, null, null, null), () -> count(inspections));

        assertThat(inspections).hasValue(2);
    }

    @Test
    void parse_errors_are_remembered_but_io_errors_are_not() {
        DatasetInspections cache = new DatasetInspections(256);
        AtomicInteger inspections = new AtomicInteger();
        DomainException broken = new DomainException(ErrorCode.FILE_PARSE_ERROR, "Broken.");

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cache.get(ID, ReadOptions.defaults(), () -> {
                inspections.incrementAndGet();
                throw broken;
            })).isSameAs(broken);
        }
        assertThat(inspections).hasValue(1);

        UUID other = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cache.get(other, ReadOptions.defaults(), () -> {
                inspections.incrementAndGet();
                throw new UncheckedIOException(new IOException("disk"));
            })).isInstanceOf(UncheckedIOException.class);
        }
        assertThat(inspections).hasValue(3);
    }

    @Test
    void the_oldest_entries_go_first_and_eviction_drops_a_dataset() {
        DatasetInspections cache = new DatasetInspections(2);
        cache.get(UUID.randomUUID(), ReadOptions.defaults(), () -> INFO);
        cache.get(ID, ReadOptions.defaults(), () -> INFO);
        cache.get(UUID.randomUUID(), ReadOptions.defaults(), () -> INFO);
        assertThat(cache.size()).isEqualTo(2);

        cache.evict(ID);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void a_writer_waits_for_readers_and_unused_locks_are_dropped() throws Exception {
        DatasetLocks locks = new DatasetLocks();
        DatasetLocks.Held first = locks.read(ID);
        DatasetLocks.Held second = locks.read(ID);

        assertThat(locks.tryWrite(ID, Duration.ofMillis(50))).isEmpty();

        CompletableFuture<Boolean> writer = CompletableFuture.supplyAsync(() -> {
            try (DatasetLocks.Held held = locks.tryWrite(ID, Duration.ofSeconds(5)).orElseThrow()) {
                return true;
            }
        });
        first.close();
        second.close();
        second.close();
        assertThat(writer.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(locks.size()).isZero();
    }

    private static TableInfo count(AtomicInteger inspections) {
        inspections.incrementAndGet();
        return INFO;
    }
}
