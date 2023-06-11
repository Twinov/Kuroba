package com.github.adamantcheese.chan.ui.text.spans;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

import com.github.adamantcheese.chan.features.theme.Theme;

import java.util.Objects;

public class ChanHighlightSpan
        extends CharacterStyle
        implements UpdateAppearance {
    private final int backgroundColor;
    private final int foregroundColor;
    private final boolean changeForeground;

    public ChanHighlightSpan(Theme theme, byte alpha, boolean changeForeground) {
        backgroundColor = ((alpha << 24) | 0x00FFFFFF) & theme.accentColorInt;
        foregroundColor = getContrastColor(backgroundColor);
        this.changeForeground = changeForeground;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.bgColor = backgroundColor;
        if (changeForeground) {
            tp.setColor(foregroundColor);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChanHighlightSpan that = (ChanHighlightSpan) o;

        return backgroundColor == that.backgroundColor && foregroundColor == that.foregroundColor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(backgroundColor, foregroundColor);
    }
}
