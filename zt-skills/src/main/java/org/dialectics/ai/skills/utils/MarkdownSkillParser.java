package org.dialectics.ai.skills.utils;

import cn.hutool.core.util.StrUtil;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.dialectics.ai.skills.domain.Skill;
import org.springframework.util.Assert;

import java.util.*;

/**
 * SKILL文档解析器
 * 将 Markdown 文本转换为标准的 Skill 对象
 */
public class MarkdownSkillParser {
    // 带YAML扩展的解析器
    private static final Parser PARSER = Parser.builder().extensions(List.of(YamlFrontMatterExtension.create())).build();
    // 文本渲染器
    private static final TextContentRenderer TEXT_RENDERER = TextContentRenderer.builder().build();

    /**
     * 提取元数据
     *
     * @param markdownText 原始 Markdown 字符串
     * @return 标准化的 SkillMetadata 对象
     */
    public static Skill.SkillMetadata parseMetadata(String markdownText) {
        if (StrUtil.isBlank(markdownText)) {
            throw new IllegalArgumentException("skill content cannot be empty");
        }
        Node document = PARSER.parse(markdownText);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);

        String name = getFirstValue(yamlVisitor.getData(), "name");
        String description = getFirstValue(yamlVisitor.getData(), "description");
        Assert.hasText(name, "skill name cannot be empty");
        Assert.hasText(description, "skill description cannot be empty");

        return new Skill.SkillMetadata(name, description);
    }

    /**
     * 解析完整skill文本内容
     *
     * @param markdownText 原始 Markdown 字符串
     * @return 标准化的 Skill 对象
     */
    public static Skill parseContent(String markdownText) {
        if (StrUtil.isBlank(markdownText)) {
            throw new IllegalArgumentException("skill content cannot be empty");
        }
        Node document = PARSER.parse(markdownText);

        // 1. 解析 YAML Front Matter (元数据)
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        String name = getFirstValue(yamlVisitor.getData(), "name");
        String description = getFirstValue(yamlVisitor.getData(), "description");
        Assert.hasText(name, "skill name cannot be empty");
        Assert.hasText(description, "skill description cannot be empty");
        Skill.SkillMetadata metadata = new Skill.SkillMetadata(name, description);

        // 2. 解析正文结构 (Visitor 模式)
        var contentVisitor = new SkillContentVisitor();
        document.accept(contentVisitor);

        // 3. 构建最终对象
        return Skill.builder()
                .metadata(metadata)
                .title(contentVisitor.getTitle())
                .instructions(contentVisitor.getInstructions())
                .examples(Collections.unmodifiableList(contentVisitor.getExamples()))
                .guidelines(Collections.unmodifiableList(contentVisitor.getGuidelines()))
                .build();
    }

    private static String getFirstValue(Map<String, List<String>> map, String key) {
        List<String> values = map.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : "";
    }

    /**
     * 核心内容提取访问者
     * <p>使用 Visitor 模式可以将遍历逻辑与业务逻辑解耦，处理不同类型的节点更清晰。
     */
    private static class SkillContentVisitor extends AbstractVisitor {

        private ParseState currentState = ParseState.INIT;
        private String title = "";
        private final StringBuilder instructionsBuilder = new StringBuilder();
        private final List<String> examples = new ArrayList<>();
        private final List<String> guidelines = new ArrayList<>();

        // 状态定义
        private enum ParseState {
            INIT, INSTRUCTION_ZONE, EXAMPLES_ZONE, GUIDELINES_ZONE, UNKNOWN
        }

        @Override
        public void visit(Heading heading) {
            String text = TEXT_RENDERER.render(heading).trim();
            int level = heading.getLevel();

            // H1 提取为标题
            if (level == 1) {
                this.title = text;
                // 标题之后，默认认为还没进入特定区域，或者直接开始指令区（视具体约定而定，这里重置状态）
                this.currentState = ParseState.INSTRUCTION_ZONE;
            }
            // H2 用于切换状态区域
            else if (level == 2) {
                if (text.contains("指令")) {
                    this.currentState = ParseState.INSTRUCTION_ZONE;
                } else if (text.contains("示例")) {
                    this.currentState = ParseState.EXAMPLES_ZONE;
                } else if (text.contains("指南")) {
                    this.currentState = ParseState.GUIDELINES_ZONE;
                } else {
                    this.currentState = ParseState.UNKNOWN;
                }
            }
            // 继续访问子节点（通常 Heading 内部只有 Text，但以防万一）
            visitChildren(heading);
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            // 关键修复：你的模板中，指令是在 ```txt ... ``` 代码块中的
            // 因此必须处理 FencedCodeBlock
            if (currentState == ParseState.INSTRUCTION_ZONE) {
                if (!instructionsBuilder.isEmpty()) {
                    instructionsBuilder.append("\n");
                }
                // 使用 getLiteral() 获取原始内容，保留换行符，这对 Prompt 很重要
                instructionsBuilder.append(codeBlock.getLiteral().trim());
            }
        }

        @Override
        public void visit(Paragraph paragraph) {
            // 在 Visitor 模式中，Paragraph 可能会作为 ListItem 的子节点出现。
            // 我们需要判断父节点是否是 Document，以避免将列表中的内容重复添加到 instructionsBuilder
            if (paragraph.getParent() instanceof Document) {
                if (currentState == ParseState.INSTRUCTION_ZONE) {
                    String text = TEXT_RENDERER.render(paragraph).trim();
                    if (!text.isEmpty()) {
                        if (!instructionsBuilder.isEmpty()) instructionsBuilder.append("\n");
                        instructionsBuilder.append(text);
                    }
                }
            }
            // 如果是列表内的段落，交给 visit(BulletList) 或让其自然递归处理
            visitChildren(paragraph);
        }

        @Override
        public void visit(BulletList bulletList) {
            // 处理列表区域
            if (currentState == ParseState.EXAMPLES_ZONE) {
                extractListItems(bulletList, examples);
            } else if (currentState == ParseState.GUIDELINES_ZONE) {
                extractListItems(bulletList, guidelines);
            }
            // 这里不调用 visitChildren，因为我们已经手动提取了列表项，避免重复访问
        }

        /**
         * 专门用于提取列表项内容的辅助方法
         */
        private void extractListItems(BulletList list, List<String> target) {
            Node node = list.getFirstChild();
            while (node != null) {
                if (node instanceof ListItem) {
                    // 渲染整个 ListItem 的文本内容（自动处理内部的 Paragraph 等）
                    // 这里的 stripIndent 是为了去除多余空白
                    String text = TEXT_RENDERER.render(node).trim();
                    // 去除可能的列表符号（TextContentRenderer 可能会带上 '- '）
                    // 但通常 Renderer 渲染 ListItem 会保留结构。
                    // 更加稳妥的方式是：只取 ListItem 下的 Paragraph 文本

                    StringBuilder itemContent = new StringBuilder();
                    Node child = node.getFirstChild();
                    while (child != null) {
                        if (child instanceof Paragraph || child instanceof Text) {
                            itemContent.append(TEXT_RENDERER.render(child));
                        }
                        child = child.getNext();
                    }
                    String finalContent = itemContent.toString().trim();
                    if (!finalContent.isEmpty()) {
                        target.add(finalContent);
                    }
                }
                node = node.getNext();
            }
        }

        // Getters
        public String getTitle() {
            return title;
        }

        public String getInstructions() {
            return instructionsBuilder.toString().trim();
        }

        public List<String> getExamples() {
            return examples;
        }

        public List<String> getGuidelines() {
            return guidelines;
        }
    }
}
