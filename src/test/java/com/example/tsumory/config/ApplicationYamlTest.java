package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

// 消すと既定の値に戻り、すぐには気づけない設定を確かめる（docs/phase1-spec.md の「9. 設定」）
class ApplicationYamlTest {

    private static String property(String key) {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yaml"));
        Properties properties = factory.getObject();
        return String.valueOf(properties.get(key));
    }

    @Test
    void JPAのセッションをリクエストの終わりまで開いたままにしない() {
        // 既定（有効）だと、Claude API の応答を待つ間も DB 接続を握り続ける
        assertThat(property("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void 起動時にエンティティとスキーマを突き合わせ作り替えない() {
        // スキーマは Flyway で作る。update・create にしない（CLAUDE.md の禁止事項）
        assertThat(property("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
