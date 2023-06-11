package com.github.adamantcheese.chan.ui.text.spans.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.features.theme.Theme;

/**
 * value is the board code
 */
public class BoardLinkable
        extends PostLinkable<String> {
    public BoardLinkable(
            @NonNull Theme theme, String value
    ) {
        super(theme, value);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(quoteColor);
        textPaint.setUnderlineText(true);
    }
}
