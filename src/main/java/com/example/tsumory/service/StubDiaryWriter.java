package com.example.tsumory.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 仮実装。Claude API を呼ぶ DiaryAiService（本物の DiaryWriter）が完成したら、この @Component を外す／クラスごと削除して
// 差し替える。最終状態にこのクラスは残らない。
@Component
public class StubDiaryWriter implements DiaryWriter {

    @Override
    public String write(LocalDate date, List<String> posts) {
        var body = posts.stream().collect(Collectors.joining("\n"));
        return """
                [%sの日記（仮）]
                %s
                """.formatted(date, body);
    }
}
