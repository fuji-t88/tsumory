package com.example.tsumory.service;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;

/**
 * Claude API を呼ぶ 2 つのクラス（{@link ClaudeService}・{@link PostCategoryClassifier}）で共通の処理。プロンプトへの埋め込み方と、
 * 失敗のログに出す値を 1 か所にまとめ、片方だけが変わらないようにする。
 */
final class ClaudeApiSupport {

    private static final long NANOS_PER_MILLI = 1_000_000;

    private static final String NONE = "none";

    private ClaudeApiSupport() {}

    /** ユーザーメッセージの区切り（{@code <posts>}・{@code <post>}）を本文で壊されないよう、{@code &}・{@code <}・{@code >} をエスケープする。 */
    static String escapeForPrompt(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** {@code System.nanoTime()} で取った開始時刻からの経過ミリ秒。 */
    static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }

    /** API がエラーを返したときの HTTP ステータス。応答がないとき（ネットワーク障害・タイムアウトなど）は {@code none}。 */
    static String statusCode(AnthropicException e) {
        return e instanceof AnthropicServiceException se ? String.valueOf(se.statusCode()) : NONE;
    }

    /** API がエラーを返したときの {@code request-id}（Anthropic への問い合わせに使う）。応答がないときは {@code none}。 */
    static String requestId(AnthropicException e) {
        return e instanceof AnthropicServiceException se
                ? se.headers().values("request-id").stream().findFirst().orElse(NONE)
                : NONE;
    }
}
