package com.github.adamantcheese.chan.features.embedding.embedders.base;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.util.LruCache;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.Converter;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.*;

public interface Embedder
        extends Converter<EmbedResult, Response> {

    // acts like a constructor for subclasses, called when EmbeddingEngine initializes
    default void setup(CookieJar cookieJar) {}

    /**
     * @param comment A copy of the post's initial data
     * @return true if this embedder should be run on this content
     */
    boolean shouldEmbed(CharSequence comment);

    default boolean shouldCacheResults() {
        return true;
    }

    default int getTimeoutMillis() {
        return (int) TimeUnit.SECONDS.toMillis(3);
    }

    default Headers getExtraHeaders() {
        return null;
    }

    /**
     * This is used for the helper calls in EmbeddingEngine for a "standard" embed of icon-title-duration.
     *
     * @return A bitmap representing this embedder, if used for the replacement
     */
    Bitmap getIconBitmap();

    /**
     * @return A pattern that will match the text to be replaced with the embed, usually a URL
     */
    Pattern getEmbedReplacePattern();

    /**
     * @param matcher The matcher for the embed pattern above, if needed for generating the URL
     * @return A URL that requests should be sent to in order to retrieve information for the embedding
     */
    HttpUrl generateRequestURL(Matcher matcher);

    /**
     * @param theme              The current theme, for post linkables (generally is ThemeHelper.getCurrentTheme())
     * @param commentCopy        A copy of the post's comment, to which spans can be attached
     * @param generatedImages    A list of images that will be added to the original post after everything is complete
     * @param videoTitleDurCache A cache of url to video titles that can be referenced and added/removed from.
     * @return A list of pairs of call/callback that will do the embedding. A post may have more than one thing to be embedded.
     * Calls should NOT be enqueued, as the embedding engine will take care of enqueuing the appropriate call/callback pair.
     */
    default List<Pair<Call, Callback>> generateCallPairs(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            LruCache<String, EmbedResult> videoTitleDurCache
    ) {
        return addStandardEmbedCalls(this, theme, commentCopy, generatedImages, videoTitleDurCache);
    }

    /**
     * This is used by helper calls in EmbeddingEngine to automatically process a returned result.
     *
     * @param response The response to convert into an EmbedResult (usually either a Document or JSONReader)
     * @return An embed result for the call, consisting of a title, duration, and an optional extra post image
     */
    EmbedResult convert(Response response)
            throws Exception;
}

