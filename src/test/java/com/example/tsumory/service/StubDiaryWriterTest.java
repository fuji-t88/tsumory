package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubDiaryWriterTest {

    @Test
    void write_joinsPostsUnderStubHeader() {
        var diary = new StubDiaryWriter().write(LocalDate.of(2026, 10, 7), List.of("寝坊した", "昼のカレーが辛すぎた"));

        assertThat(diary).isEqualTo("""
                [2026-10-07の日記（仮）]
                寝坊した
                昼のカレーが辛すぎた
                """);
    }
}
