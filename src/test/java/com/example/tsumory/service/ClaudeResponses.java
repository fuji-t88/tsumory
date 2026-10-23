package com.example.tsumory.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import java.util.Arrays;
import java.util.Optional;

/** Claude API の応答（{@link Message}）を、テストで使う項目だけ埋めて作る。 */
final class ClaudeResponses {

    static final String MESSAGE_ID = "msg_test";

    private ClaudeResponses() {}

    /** 止まった理由と、テキストのブロックを順に並べた応答。{@code stopReason} が {@code null} なら、止まった理由のない応答。 */
    static Message response(StopReason stopReason, String... texts) {
        var usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(120L);
        when(usage.outputTokens()).thenReturn(80L);

        var message = mock(Message.class);
        when(message.id()).thenReturn(MESSAGE_ID);
        when(message.usage()).thenReturn(usage);
        when(message.stopReason()).thenReturn(Optional.ofNullable(stopReason));
        when(message.stopDetails()).thenReturn(Optional.empty());
        var blocks = Arrays.stream(texts).map(ClaudeResponses::textBlock).toList();
        when(message.content()).thenReturn(blocks);
        return message;
    }

    private static ContentBlock textBlock(String text) {
        var textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(text);
        var block = mock(ContentBlock.class);
        when(block.text()).thenReturn(Optional.of(textBlock));
        return block;
    }
}
