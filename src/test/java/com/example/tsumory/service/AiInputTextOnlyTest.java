package com.example.tsumory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tsumory.domain.PostPhoto;
import com.example.tsumory.repository.PostPhotoRepository;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Claude API を呼ぶクラス（日記の生成とカテゴリの分類）が、写真に触れないことを確かめる（phase3-spec.md の「6. 処理の詳細 > 共通の決まり」、
 * CM-REQ-02）。写真の型をフィールド・引数・戻り値に持たなければ、写真のデータを AI に渡す経路はない。改修で写真を足したら、ここで気づく。
 */
class AiInputTextOnlyTest {

    private static final Set<Class<?>> PHOTO_TYPES = Set.of(
            PostPhoto.class,
            PostPhotoRepository.class,
            PhotoUpload.class,
            PhotoContent.class,
            PhotoChange.class,
            PhotoValidator.class,
            PhotoType.class);

    /** そのクラスが、フィールド・コンストラクタとメソッドの引数・戻り値で使っている型。 */
    private static List<Class<?>> typesUsedBy(Class<?> type) {
        var used = new ArrayList<Class<?>>();
        Arrays.stream(type.getDeclaredFields()).forEach(field -> used.add(field.getType()));
        Stream.<Executable>concat(
                        Arrays.stream(type.getDeclaredConstructors()), Arrays.stream(type.getDeclaredMethods()))
                .forEach(executable -> used.addAll(List.of(executable.getParameterTypes())));
        Arrays.stream(type.getDeclaredMethods()).forEach(method -> used.add(method.getReturnType()));
        return used;
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                ClaudeService.class,
                ClaudeApiSupport.class,
                DiaryWriter.class,
                DiaryGenerationService.class,
                PostCategoryClassifier.class
            })
    void ClaudeのAPIを呼ぶクラスは写真の型を使わない(Class<?> aiClass) {
        assertThat(typesUsedBy(aiClass)).doesNotContainAnyElementsOf(PHOTO_TYPES);
    }
}
