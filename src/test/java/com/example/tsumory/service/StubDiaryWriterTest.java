package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubDiaryWriterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 7);
    private static final List<String> CONTENTS = List.of("寝坊した", "昼のカレーが辛すぎた");

    @Test
    void written_returnsContentsInOrderWithStubHeader() {
        var writer = new StubDiaryWriter(Duration.ZERO, "written");

        assertThat(writer.write(DATE, CONTENTS)).isEqualTo(StubDiaryWriter.HEADER + "\n\n寝坊した\n昼のカレーが辛すぎた");
    }

    @Test
    void refused_throwsRefused() {
        var writer = new StubDiaryWriter(Duration.ZERO, "refused");

        assertThatThrownBy(() -> writer.write(DATE, CONTENTS)).isInstanceOf(DiaryRefusedException.class);
    }

    @Test
    void failed_throwsWriteFailed() {
        var writer = new StubDiaryWriter(Duration.ZERO, "failed");

        assertThatThrownBy(() -> writer.write(DATE, CONTENTS)).isInstanceOf(DiaryWriteFailedException.class);
    }
}
