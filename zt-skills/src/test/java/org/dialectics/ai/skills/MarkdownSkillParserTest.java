package org.dialectics.ai.skills;

import org.dialectics.ai.skills.domain.Skill;
import org.dialectics.ai.skills.utils.MarkdownSkillParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Markdown Skill 解析器核心测试")
class MarkdownSkillParserTest {

    // -------------------------------------------------------------------------------------
    // 1. 元数据解析测试 (parseMetadata & parseContent 的元数据部分)
    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("元数据契约校验 (Validation)")
    class MetadataValidationTest {

        @Test
        @DisplayName("正常场景: 包含完整 name 和 description")
        void should_pass_when_metadata_is_complete() {
            var markdown = """
                    ---
                    name: java-architect
                    description: 资深Java架构师
                    ---
                    # content
                    """;

            Skill.SkillMetadata metadata = MarkdownSkillParser.parseMetadata(markdown);

            assertThat(metadata.name()).isEqualTo("java-architect");
            assertThat(metadata.description()).isEqualTo("资深Java架构师");
        }

        @Test
        @DisplayName("异常场景: 缺少 name 字段应抛出异常")
        void should_throw_when_name_is_missing() {
            var markdown = """
                    ---
                    description: 只有描述没有名称
                    ---
                    """;

            assertThatThrownBy(() -> MarkdownSkillParser.parseMetadata(markdown))
                    .isInstanceOf(IllegalArgumentException.class) // Hutool Assert 默认抛出 IAE
                    .hasMessageContaining("skill name cannot be empty");
        }

        @Test
        @DisplayName("异常场景: 缺少 description 字段应抛出异常")
        void should_throw_when_description_is_missing() {
            var markdown = """
                    ---
                    name: unnamed-skill
                    ---
                    """;

            assertThatThrownBy(() -> MarkdownSkillParser.parseContent(markdown))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skill description cannot be empty");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("边界测试: 输入为空字符串时应抛出异常")
        void should_throw_when_input_is_blank(String input) {
            assertThatThrownBy(() -> MarkdownSkillParser.parseContent(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skill content cannot be empty");
        }
    }

    // -------------------------------------------------------------------------------------
    // 2. 正文解析测试 (parseContent)
    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("正文内容解析 (Parsing Logic)")
    class ContentParsingTest {

        @Test
        @DisplayName("核心场景: 标准模板完整解析 (Happy Path)")
        void should_parse_standard_full_template() {
            // Given: 一个包含代码块指令、列表的完整 Markdown
            var markdown = """
                    ---
                    name: translator
                    description: 中英互译专家
                    ---

                    # Translation Role

                    ## 指令
                    ```txt
                    1. 保持信达雅。
                    2. 不要翻译专有名词。
                    ```

                    ## 示例
                    - 输入: Hello World
                    - 输出: 你好世界

                    ## 指南
                    - 始终使用中文回答
                    """;

            // When
            Skill skill = MarkdownSkillParser.parseContent(markdown);

            // Then: 验证各个部分
            assertThat(skill.getTitle()).isEqualTo("Translation Role");

            // 验证指令保留了换行符，且去除了 ```txt
            assertThat(skill.getInstructions())
                    .contains("1. 保持信达雅。")
                    .contains("\n")
                    .contains("2. 不要翻译专有名词。")
                    .doesNotContain("```txt");

            assertThat(skill.getExamples())
                    .hasSize(2)
                    .containsExactly("输入: Hello World", "输出: 你好世界");

            assertThat(skill.getGuidelines())
                    .hasSize(1)
                    .contains("始终使用中文回答");
        }

        @Test
        @DisplayName("指令解析: 兼容普通段落写法 (非代码块)")
        void should_parse_instructions_from_plain_paragraph() {
            var markdown = """
                    ---
                    name: test
                    description: test
                    ---
                    # Title
                    ## 指令
                    这是一条普通文本指令。
                    这是第二行指令。
                    """;

            Skill skill = MarkdownSkillParser.parseContent(markdown);

            // 验证 Paragraph 处理逻辑会将多行文本拼接
            assertThat(skill.getInstructions())
                    .contains("这是一条普通文本指令。")
                    .contains("这是第二行指令。");
        }

        @Test
        @DisplayName("混合解析: 同时存在段落和代码块时应正确拼接")
        void should_combine_paragraph_and_codeblock_instructions() {
            var markdown = """
                    ---
                    name: test
                    description: test
                    ---
                    # Title
                    ## 指令
                    请遵循以下代码：
                    ```txt
                    SYSTEM_PROMPT = TRUE
                    ```
                    """;

            Skill skill = MarkdownSkillParser.parseContent(markdown);

            // 验证拼接逻辑
            assertThat(skill.getInstructions())
                    .isEqualTo("请遵循以下代码：\nSYSTEM_PROMPT = TRUE");
        }
    }

    // -------------------------------------------------------------------------------------
    // 3. 状态机与结构容错测试 (State Machine & Robustness)
    // -------------------------------------------------------------------------------------
    @Nested
    @DisplayName("结构容错与状态机 (Robustness)")
    class StateMachineTest {

        @Test
        @DisplayName("乱序测试: 即使 H2 标题顺序不同，也能正确归类内容")
        void should_handle_sections_in_any_order() {
            // Given: 指南在示例之前，且包含干扰项
            var markdown = """
                    ---
                    name: sort-test
                    description: desc
                    ---
                    # Title

                    ## 指南
                    - 这是一个指南

                    ## 未知区域
                    - 这里的内容应该被忽略

                    ## 示例
                    - 这是一个示例
                    """;

            // When
            Skill skill = MarkdownSkillParser.parseContent(markdown);

            // Then
            assertThat(skill.getGuidelines()).containsExactly("这是一个指南");
            assertThat(skill.getExamples()).containsExactly("这是一个示例");
            // 确保没有把未知区域的内容解析进去
            assertThat(skill.getInstructions()).isEmpty();
        }

        @Test
        @DisplayName("列表清洗: 应去除列表项中的多余格式(加粗等)")
        void should_clean_list_item_formatting() {
            // Given: 列表项中包含 Bold 或 Code 标记
            var markdown = """
                    ---
                    name: format-clean
                    description: desc
                    ---
                    # Title
                    ## 示例
                    - 用户: **加粗问题**的内容
                    - 助手: `Code` 回答
                    """;

            Skill skill = MarkdownSkillParser.parseContent(markdown);

            // 代码使用了 textRenderer.render(child)，会将 **加粗** 渲染为纯文本 "加粗" (视配置而定)
            // CommonMark 默认 TextContentRenderer 通常会保留文本内容但去除标记符号
            assertThat(skill.getExamples())
                    .anyMatch(s -> s.contains("用户: 加粗问题的内容"))
                    .anyMatch(s -> s.contains("助手: \"Code\" 回答"));
        }

        @Test
        @DisplayName("部分缺失: 仅有指令，无列表时，List 字段不为 Null")
        void should_return_empty_list_instead_of_null() {
            var markdown = """
                    ---
                    name: minimal
                    description: desc
                    ---
                    # Minimal
                    ## 指令
                    Do it.
                    """;

            Skill skill = MarkdownSkillParser.parseContent(markdown);

            assertThat(skill.getExamples()).isNotNull().isEmpty();
            assertThat(skill.getGuidelines()).isNotNull().isEmpty();
        }
    }
}
