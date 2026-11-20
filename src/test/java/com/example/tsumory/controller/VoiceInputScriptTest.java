package com.example.tsumory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 音声入力のスクリプト（{@code static/js/voice-input.js}）が、音声をサーバーに送らず、録音もしないことを、ソースの中身で確かめる（phase3-spec.md の
 * 「5. 画面 > 音声入力」、VC-REQ-09）。JavaScript のテストの仕組みはないので、使ってはいけない API が書かれていないことで守る。
 */
class VoiceInputScriptTest {

    private static String script;

    @BeforeAll
    static void readScript() throws IOException {
        script = Files.readString(Path.of("src/main/resources/static/js/voice-input.js"), StandardCharsets.UTF_8);
    }

    @Test
    void 音声やその文字をどこにも送らない() {
        // 認識した文字は本文の欄に入れるだけで、送るのはユーザーが投稿ボタンを押したときのフォームだけ
        assertThat(script)
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("sendBeacon")
                .doesNotContain("WebSocket")
                .doesNotContain("EventSource");
    }

    @Test
    void 録音もマイクの直接の取得もしない() {
        // マイクを使うのはブラウザの音声認識だけ。音声のデータを手元に取り出さない
        assertThat(script)
                .doesNotContain("MediaRecorder")
                .doesNotContain("getUserMedia")
                .doesNotContain("AudioContext");
    }

    @Test
    void 認識した文字はHTMLとして解釈させない() {
        // 「script」などと話しても、文字として入るだけにする（textarea の value と textContent だけを使う）
        assertThat(script)
                .doesNotContain("innerHTML")
                .doesNotContain("outerHTML")
                .doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write");
    }

    @Test
    void 日本語で認識し確定した文字だけを本文に入れる() {
        assertThat(script).contains("recognition.lang = 'ja-JP';").contains("if (event.results[i].isFinal) {");
    }
}
