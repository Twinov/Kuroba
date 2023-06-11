package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.utils.StringUtils.RenderOrder.RENDER_NORMAL;
import static com.github.adamantcheese.chan.utils.StringUtils.makeSpanOptions;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.*;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.ui.text.spans.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.spans.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.vdurmont.emoji.EmojiParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing style actions that are applied to terminal text nodes.
 */
public class CommonStyleActions {
    public static final StyleAction NO_OP = (node, text) -> text == null ? "" : text;
    public static final StyleAction NULLIFY = (element, text) -> "";
    public static final StyleAction BLOCK_LINE_BREAK =
            (element, text) -> new SpannableStringBuilder(text).append(element.nextSibling() != null ? "\n" : "");
    public static final StyleAction NEWLINE = (element, text) -> "\n";
    public static final StyleAction SRC_ATTR = (element, text) -> element.attr("src");
    public static final StyleAction CHOMP = (element, text) -> StringUtils.chomp(text);
    public static final StyleAction UNDERLINE = (element, text) -> span(text, new UnderlineSpan());
    public static final StyleAction ITALICIZE = (element, text) -> span(text, new StyleSpan(Typeface.ITALIC));
    public static final StyleAction BOLD = (element, text) -> span(text, new StyleSpan(Typeface.BOLD));
    public static final StyleAction STRIKETHROUGH = (element, text) -> span(text, new StrikethroughSpan());
    public static final StyleAction MONOSPACE = (element, text) -> span(text, new TypefaceSpan("monospace"));
    public static final StyleAction A_HREF = (element, text) -> span(text, new URLSpan(element.attr("href")));

    public static StyleAction EMOJI = (node, text) -> {
        String str = text == null ? "" : text.toString();
        return ChanSettings.enableEmoji.get() ? EmojiParser.parseToUnicode(str) : str;
    };

    private static final Pattern HEX_COLOR_PATTERN =
            Pattern.compile("(?<![\\w/])#([a-fA-F0-9]{2})([a-fA-F0-9]{2})([a-fA-F0-9]{2})([a-fA-F0-9]{2})?");
    public static final StyleAction HEX_COLOR = (element, text) -> {
        SpannableStringBuilder newBuilder = new SpannableStringBuilder(text);
        Matcher colorMatcher = HEX_COLOR_PATTERN.matcher(newBuilder);
        while (colorMatcher.find()) {
            int color = Color.parseColor(colorMatcher.group());
            newBuilder.setSpan(
                    new BackgroundColorSpanHashed(color),
                    colorMatcher.start(),
                    colorMatcher.end(),
                    makeSpanOptions(RENDER_NORMAL)
            );
            newBuilder.setSpan(
                    new ForegroundColorSpanHashed(AndroidUtils.getContrastColor(color)),
                    colorMatcher.start(),
                    colorMatcher.end(),
                    makeSpanOptions(RENDER_NORMAL)
            );
        }
        return newBuilder;
    };
}
