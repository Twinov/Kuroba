package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.features.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.Map;

public abstract class PostThemedStyleAction
        extends AdditionalDataStyleAction {
    private static final String THEME_DATA = "theme";
    private static final String POST_DATA = "post";
    private static final String POST_CALLBACK_DATA = "post_callback";

    public final AdditionalDataStyleAction with(
            Theme theme, Post.Builder post, PostParser.PostParserCallback callback
    ) {
        AdditionalDataStyleAction newAction = new AdditionalDataStyleAction() {
            @NonNull
            @Override
            protected CharSequence style(
                    @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
            ) {
                return PostThemedStyleAction.this.style(node, text, data);
            }
        };
        newAction.data.put(THEME_DATA, theme);
        newAction.data.put(POST_DATA, post);
        newAction.data.put(POST_CALLBACK_DATA, callback);
        return newAction;
    }

    @NonNull
    protected abstract CharSequence style(
            @NonNull Node node,
            @Nullable CharSequence text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.PostParserCallback callback
    );

    @NonNull
    @Override
    protected final CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
    ) {
        Theme theme = (Theme) data.get(THEME_DATA);
        Post.Builder post = (Post.Builder) data.get(POST_DATA);
        PostParser.PostParserCallback callback = (PostParser.PostParserCallback) data.get(POST_CALLBACK_DATA);
        if (theme == null || post == null || callback == null) {
            throw new IllegalStateException("Cannot style with missing info! Call with() beforehand!");
        }
        return style(node, text, theme, post, callback);
    }
}
