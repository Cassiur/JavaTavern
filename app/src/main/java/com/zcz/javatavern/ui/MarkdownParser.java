package com.zcz.javatavern.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 Markdown 解析器 —— 纯 Java，无 Android 依赖，便于 JVM 单元测试。
 *
 * <p>只覆盖 AI 角色扮演输出里真正常见的语法：粗体、斜体、行内代码、围栏代码块、
 * 引用、无序/有序列表、标题与分隔线。刻意不引入第三方 Markdown 库，保持项目
 * 「零额外依赖」的取向。
 *
 * <p>行内解析采用「配对标记 + 边界分段」策略：先把成对的标记找出来，收集全部
 * 边界点切分文本，再判断每一段落在哪些样式区间内。这样既能正确处理嵌套
 * （{@code **粗体里的 *斜体* 部分**}），也能自然跳过标记符号本身；
 * 未成对的标记不会匹配，因而原样保留为普通文本。
 */
public final class MarkdownParser {

    /** 一个块级元素。 */
    public static final class Block {
        public enum Kind {
            PARAGRAPH,
            HEADING,
            QUOTE,
            BULLET_ITEM,
            ORDERED_ITEM,
            CODE_BLOCK,
            DIVIDER
        }

        private final Kind kind;
        private final int level;
        private final String language;
        private final List<Inline> inlines;
        private final String code;

        private Block(Kind kind, int level, String language, List<Inline> inlines, String code) {
            this.kind = kind;
            this.level = level;
            this.language = language;
            this.inlines = inlines;
            this.code = code;
        }

        public Kind getKind() {
            return kind;
        }

        /** 标题层级（1-6）；列表缩进层级从 0 起；其余为 0。 */
        public int getLevel() {
            return level;
        }

        /** 围栏代码块的语言标记，可为空字符串。 */
        public String getLanguage() {
            return language;
        }

        /** 行内片段；代码块与分隔线为空列表。 */
        public List<Inline> getInlines() {
            return inlines;
        }

        /** 围栏代码块的原始内容（不含围栏行）。 */
        public String getCode() {
            return code;
        }

        public boolean isCodeBlock() {
            return kind == Kind.CODE_BLOCK;
        }

        public boolean isDivider() {
            return kind == Kind.DIVIDER;
        }
    }

    /** 一段带样式的行内文本。 */
    public static final class Inline {
        private final String text;
        private final boolean bold;
        private final boolean italic;
        private final boolean strikethrough;
        private final boolean code;

        private Inline(String text, boolean bold, boolean italic, boolean strikethrough, boolean code) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.strikethrough = strikethrough;
            this.code = code;
        }

        public String getText() {
            return text;
        }

        public boolean isBold() {
            return bold;
        }

        public boolean isItalic() {
            return italic;
        }

        public boolean isStrikethrough() {
            return strikethrough;
        }

        public boolean isCode() {
            return code;
        }

        public boolean isPlain() {
            return !bold && !italic && !strikethrough && !code;
        }
    }

    private enum Style { BOLD, ITALIC, STRIKE, CODE }

    private static final Pattern PATTERN_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern PATTERN_STRIKE = Pattern.compile("~~(.+?)~~");
    private static final Pattern PATTERN_CODE = Pattern.compile("`([^`\\n]+?)`");
    private static final Pattern PATTERN_ITALIC =
            Pattern.compile("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)");
    private static final Pattern PATTERN_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern PATTERN_BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern PATTERN_ORDERED = Pattern.compile("^(\\s*)\\d+[.)]\\s+(.*)$");
    private static final Pattern PATTERN_DIVIDER = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern PATTERN_FENCE = Pattern.compile("^\\s*```\\s*([\\w+#-]*)\\s*$");
    private static final Pattern PATTERN_QUOTE = Pattern.compile("^\\s*>\\s?(.*)$");

    private MarkdownParser() {
    }

    /** 把 Markdown 文本解析为块序列。空输入返回空列表。 */
    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> paragraph = new ArrayList<>();
        boolean inFence = false;
        String fenceLanguage = "";
        List<String> codeLines = new ArrayList<>();

        for (String line : lines) {
            Matcher fence = PATTERN_FENCE.matcher(line);
            if (fence.matches()) {
                if (inFence) {
                    blocks.add(codeBlock(fenceLanguage, codeLines));
                    codeLines.clear();
                    fenceLanguage = "";
                    inFence = false;
                } else {
                    flushParagraph(blocks, paragraph);
                    inFence = true;
                    fenceLanguage = fence.group(1);
                }
                continue;
            }
            if (inFence) {
                codeLines.add(line);
                continue;
            }
            if (line.trim().isEmpty()) {
                flushParagraph(blocks, paragraph);
                continue;
            }
            if (PATTERN_DIVIDER.matcher(line).matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(Block.Kind.DIVIDER, 0, "", List.of(), ""));
                continue;
            }
            Matcher heading = PATTERN_HEADING.matcher(line);
            if (heading.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(
                        Block.Kind.HEADING,
                        heading.group(1).length(),
                        "",
                        parseInlines(heading.group(2).trim()),
                        ""
                ));
                continue;
            }
            Matcher quote = PATTERN_QUOTE.matcher(line);
            if (quote.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(
                        Block.Kind.QUOTE, 0, "", parseInlines(quote.group(1).trim()), ""));
                continue;
            }
            Matcher ordered = PATTERN_ORDERED.matcher(line);
            if (ordered.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(
                        Block.Kind.ORDERED_ITEM,
                        indentLevel(ordered.group(1)),
                        "",
                        parseInlines(ordered.group(2).trim()),
                        ""
                ));
                continue;
            }
            Matcher bullet = PATTERN_BULLET.matcher(line);
            if (bullet.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(
                        Block.Kind.BULLET_ITEM,
                        indentLevel(bullet.group(1)),
                        "",
                        parseInlines(bullet.group(2).trim()),
                        ""
                ));
                continue;
            }
            paragraph.add(line.trim());
        }

        if (inFence) {
            // 未闭合的围栏：按代码块收尾，避免内容被吞掉。
            blocks.add(codeBlock(fenceLanguage, codeLines));
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    private static Block codeBlock(String language, List<String> codeLines) {
        return new Block(
                Block.Kind.CODE_BLOCK,
                0,
                language == null ? "" : language,
                List.of(),
                String.join("\n", codeLines)
        );
    }

    private static void flushParagraph(List<Block> blocks, List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        String text = String.join(" ", paragraph);
        paragraph.clear();
        blocks.add(new Block(Block.Kind.PARAGRAPH, 0, "", parseInlines(text), ""));
    }

    private static int indentLevel(String leadingWhitespace) {
        return Math.max(0, leadingWhitespace.replace("\t", "  ").length() / 2);
    }

    /**
     * 解析行内样式。未成对的标记不会形成样式区间，因此原样出现在输出文本中。
     */
    private static List<Inline> parseInlines(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<Mark> marks = new ArrayList<>();
        collect(text, PATTERN_CODE, Style.CODE, marks);
        collect(text, PATTERN_BOLD, Style.BOLD, marks);
        collect(text, PATTERN_STRIKE, Style.STRIKE, marks);
        collect(text, PATTERN_ITALIC, Style.ITALIC, marks);

        if (marks.isEmpty()) {
            return List.of(new Inline(text, false, false, false, false));
        }

        TreeSet<Integer> boundaries = new TreeSet<>();
        boundaries.add(0);
        boundaries.add(text.length());
        for (Mark mark : marks) {
            boundaries.add(mark.markStart);
            boundaries.add(mark.contentStart);
            boundaries.add(mark.contentEnd);
            boundaries.add(mark.markEnd);
        }

        List<Integer> points = new ArrayList<>(boundaries);
        List<Inline> inlines = new ArrayList<>();
        for (int index = 0; index + 1 < points.size(); index++) {
            int start = points.get(index);
            int end = points.get(index + 1);
            if (start >= end || isMarkerGlyphRange(start, end, marks)) {
                continue;
            }
            boolean bold = false;
            boolean italic = false;
            boolean strike = false;
            boolean code = false;
            for (Mark mark : marks) {
                if (start >= mark.contentStart && end <= mark.contentEnd) {
                    switch (mark.style) {
                        case BOLD -> bold = true;
                        case ITALIC -> italic = true;
                        case STRIKE -> strike = true;
                        case CODE -> code = true;
                    }
                }
            }
            inlines.add(new Inline(text.substring(start, end), bold, italic, strike, code));
        }
        return inlines;
    }

    private static void collect(String text, Pattern pattern, Style style, List<Mark> marks) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int markStart = matcher.start();
            int markEnd = matcher.end();
            marks.add(new Mark(markStart, markStart + matcher.group(0).indexOf(matcher.group(1)),
                    markStart + matcher.group(0).indexOf(matcher.group(1)) + matcher.group(1).length(),
                    markEnd, style));
        }
    }

    private static boolean isMarkerGlyphRange(int start, int end, List<Mark> marks) {
        for (Mark mark : marks) {
            boolean inLeadingGlyph = start >= mark.markStart && end <= mark.contentStart;
            boolean inTrailingGlyph = start >= mark.contentEnd && end <= mark.markEnd;
            if (inLeadingGlyph || inTrailingGlyph) {
                return true;
            }
        }
        return false;
    }

    private static final class Mark {
        private final int markStart;
        private final int contentStart;
        private final int contentEnd;
        private final int markEnd;
        private final Style style;

        private Mark(int markStart, int contentStart, int contentEnd, int markEnd, Style style) {
            this.markStart = markStart;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
            this.markEnd = markEnd;
            this.style = style;
        }
    }
}
