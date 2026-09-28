package com.universaldatatools.core.table;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** core-02 task 14 (design IO11). */
class KeyHasherTest {

    private final KeyHasher hasher = new KeyHasher();

    @Test
    void parts_are_length_prefixed() {
        assertThat(hasher.hash(List.of("ab", "c"))).isNotEqualTo(hasher.hash(List.of("a", "bc")));
    }

    @Test
    void null_is_not_the_empty_string() {
        assertThat(hasher.hash(Arrays.asList((String) null))).isNotEqualTo(hasher.hash(List.of("")));
    }

    @Test
    void the_same_key_hashes_the_same_in_any_hasher() {
        assertThat(hasher.hash(List.of("x"))).isEqualTo(new KeyHasher().hash(List.of("x")));
        assertThat(hasher.hash(List.of())).isEqualTo(new KeyHasher().hash(List.of()));
    }

    @Test
    void many_distinct_keys_do_not_collide() {
        Set<Hash128> seen = new HashSet<>();
        for (int i = 0; i < 200_000; i++) {
            seen.add(hasher.hash(List.of("k" + i)));
        }
        assertThat(seen).hasSize(200_000);
    }

    @Test
    void the_index_keeps_the_first_value() {
        KeyIndex<Integer> index = new KeyIndex<>();
        Hash128 key = hasher.hash(List.of("x"));

        assertThat(index.putIfAbsent(key, 2)).isNull();
        assertThat(index.putIfAbsent(key, 3)).isEqualTo(2);
        assertThat(index.get(key)).isEqualTo(2);
        assertThat(index.size()).isEqualTo(1);
    }
}
