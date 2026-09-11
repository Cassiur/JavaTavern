package com.zcz.javatavern.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.List;

/**
 * 把 {@link MarkdownParser} 的解析结果渲染成 Android 富文本。
 *
 * <p>背景色使用中性半透明灰（而非固定主题色），这样在「用户气泡」与
 * 「角色气泡」两种底色下、以及浅色/深色主题下都能正常显示。
 */
public final class MarkdownRenderer {

    /** 半透明中性灰，用于行内代码与代码块底色。 */
    private static final int CODE_BACKGROUND = 0x2E808080;
    private static final String DIVIDER_LINE = "────────────────────";
    private static final String QUOTE_BAR = "▏";

    private MarkdownRenderer() {
    }

    /** 把 Markdown 文本渲染为富文本；输入为空时返回空字符串。 */
    public static CharSequence render(Context context, String markdown) {
        List<MarkdownParser.Block> blocks = MarkdownParser.parse(markdown);
        if (blocks.isEmpty()) {
            return "";
        }
        float density = context.getResources().getDisplayMetrics().density;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int index = 0; index < blocks.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            appendBlock(builder, blocks.get(index), density);
        }
        return builder;
    }

    private static void appendBlock(
            SpannableStringBuilder builder,
            MarkdownParser.Block block,
            float density
    ) {
        switch (block.getKind()) {
            case CODE_BLOCK -> appendCodeBlock(builder, block, density);
            case DIVIDER -> builder.append(DIVIDER_LINE);
            case HEADING -> appendHeading(builder, block);
            case QUOTE -> appendQuote(builder, block, density);
            case BULLET_ITEM, ORDERED_ITEM -> appendListItem(builder, block, density);
            case PARAGRAPH -> appendInlines(builder, block.getInlines());
        }
    }

    private static void appendInlines(
            SpannableStringBuilder builder,
            List<MarkdownParser.Inline> inlines
    ) {
        for (MarkdownParser.Inline inline : inlines) {
            int start = builder.length();
            builder.append(inline.getText());
            int end = builder.length();
            if (start >= end) {
                continue;
            }
            applyInlineSpans(builder, start, end, inline);
        }
    }

    private static void applyInlineSpans(
            SpannableStringBuilder builder,
            int start,
            int end,
            MarkdownParser.Inline inline
    ) {
        if (inline.isBold() && inline.isItalic()) {
            setSpan(builder, new StyleSpan(Typeface.BOLD_ITALIC), start, end);
        } else if (inline.isBold()) {
            setSpan(builder, new StyleSpan(Typeface.BOLD), start, end);
        } else if (inline.isItalic()) {
            setSpan(builder, new StyleSpan(Typeface.ITALIC), start, end);
        }
        if (inline.isStrikethrough()) {
            setSpan(builder, new StrikethroughSpan(), start, end);
        }
        if (inline.isCode()) {
            setSpan(builder, new TypefaceSpan("monospace"), start, end);
            setSpan(builder, new BackgroundColorSpan(CODE_BACKGROUND), start, end);
            setSpan(builder, new RelativeSizeSpan(0.94f), start, end);
        }
    }

    private static void appendHeading(SpannableStringBuilder builder, MarkdownParser.Block block) {
        int start = builder.length();
        appendInlines(builder, block.getInlines());
        int end = builder.length();
        if (start >= end) {
            return;
        }
        float scale = switch (block.getLevel()) {
            case 1 -> 1.45f;
            case 2 -> 1.28f;
            case 3 -> 1.14f;
            default -> 1.05f;
        };
        setSpan(builder, new RelativeSizeSpan(scale), start, end);
        setSpan(builder, new StyleSpan(Typeface.BOLD), start, end);
    }

    private static void appendQuote(
            SpannableStringBuilder builder,
            MarkdownParser.Block block,
            float density
    ) {
        int start = builder.length();
        builder.append(QUOTE_BAR).append(' ');
        appendInlines(builder, block.getInlines());
        int end = builder.length();
        if (start >= end) {
            return;
        }
        setSpan(builder, new StyleSpan(Typeface.ITALIC), start, end);
        setSpan(builder, new LeadingMarginSpan.Standard((int) (10 * density)), start, end);
    }

    private static void appendListItem(
            SpannableStringBuilder builder,
            MarkdownParser.Block block,
            float density
    ) {
        int start = builder.length();
        builder.append("• ");
        appendInlines(builder, block.getInlines());
        int end = builder.length();
        if (start >= end) {
            return;
        }
        int margin = (int) ((12 + block.getLevel() * 16) * density);
        setSpan(builder, new LeadingMarginSpan.Standard(margin), start, end);
    }

    private static void appendCodeBlock(
            SpannableStringBuilder builder,
            MarkdownParser.Block block,
            float density
    ) {
        int start = builder.length();
        builder.append(block.getCode());
        int end = builder.length();
        if (start >= end) {
            return;
        }
        setSpan(builder, new TypefaceSpan("monospace"), start, end);
        setSpan(builder, new BackgroundColorSpan(CODE_BACKGROUND), start, end);
        setSpan(builder, new RelativeSizeSpan(0.92f), start, end);
        setSpan(builder, new LeadingMarginSpan.Standard((int) (8 * density)), start, end);
    }

    private static void setSpan(
            SpannableStringBuilder builder,
            Object span,
            int start,
            int end
    ) {
        builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
