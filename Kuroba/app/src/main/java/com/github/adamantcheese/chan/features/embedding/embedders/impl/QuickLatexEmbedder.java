package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import static com.github.adamantcheese.chan.core.net.NetUtilsClasses.STRING_CONVERTER;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.VoidEmbedder;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Triple;
import kotlin.random.Random;
import okhttp3.*;

public class QuickLatexEmbedder
        extends VoidEmbedder {
    private final Pattern MATH_EQN_PATTERN = Pattern.compile("\\[(math|eqn)].*?\\[/\\1]", Pattern.DOTALL);
    private final Pattern QUICK_LATEX_RESPONSE =
            Pattern.compile(".*?\r\n(\\S+)\\s.*?\\s\\d+\\s\\d+(?:\r\n([\\s\\S]+))?");

    // maps a math string to a rendered image URL
    public static LruCache<String, HttpUrl> mathCache = new LruCache<>(100);

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "[math]", "[eqn]");
    }

    @Override
    public Bitmap getIconBitmap() {
        return null; // this embedder doesn't use a bitmap title
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return MATH_EQN_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return null; // this embedder is special, see setupMathImageUrlRequest below for this URL
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            LruCache<String, EmbedResult> videoTitleDurCache
    ) {
        List<Pair<Call, Callback>> ret = new ArrayList<>();
        Set<Pair<String, String>> toReplace = new HashSet<>();
        Matcher linkMatcher = getEmbedReplacePattern().matcher(commentCopy);
        while (linkMatcher.find()) {
            String rawMath = linkMatcher.group(0);
            if (rawMath == null) continue;

            String sanitizedMath = rawMath
                    .replace("[math]", "$")
                    .replace("[eqn]", "$$")
                    .replace("[/math]", "$")
                    .replace("[/eqn]", "$$")
                    .replace("%", "%25")
                    .replace("&", "%26");
            toReplace.add(new Pair<>(rawMath, sanitizedMath));
        }

        for (Pair<String, String> math : toReplace) {
            HttpUrl imageUrl = mathCache.get(math.first);
            if (imageUrl != null) {
                // have a previous image URL
                ret.add(new Pair<>(new NetUtilsClasses.NullCall(imageUrl), new NetUtilsClasses.IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        BackgroundUtils.runOnBackgroundThread(() -> {
                            Triple<Call, Callback, Runnable> ret =
                                    generateMathSpanCalls(commentCopy, imageUrl, math.first);
                            if (ret == null || ret.getFirst() == null || ret.getSecond() == null) return;
                            try {
                                ret.getSecond().onResponse(ret.getFirst(), ret.getFirst().execute());
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }));
            } else {
                // need to request an image URL
                ret.add(new Pair<>(
                        NetUtils.applicationClient.newCall(setupMathImageUrlRequest(math.second)),
                        new NetUtilsClasses.IgnoreFailureCallback() {
                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) {
                                if (!response.isSuccessful()) {
                                    response.close();
                                    return;
                                }

                                try {
                                    String responseString = STRING_CONVERTER.convert(response);
                                    Matcher matcher = QUICK_LATEX_RESPONSE.matcher(responseString);
                                    if (matcher.matches()) {
                                        HttpUrl url = HttpUrl.get(matcher.group(1));
                                        String err = matcher.group(2);
                                        if (err == null) {
                                            mathCache.put(math.first, url);
                                            Triple<Call, Callback, Runnable> ret =
                                                    generateMathSpanCalls(commentCopy, url, math.first);
                                            if (ret == null || ret.getFirst() == null || ret.getSecond() == null)
                                                return;
                                            ret.getSecond().onResponse(ret.getFirst(), ret.getFirst().execute());
                                        }
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    response.close();
                                }
                            }
                        }
                ));
            }
        }
        return ret;
    }

    private Request setupMathImageUrlRequest(String formula) {
        //@formatter:off
        String postBody =
                "formula=" + formula +
                "&fsize=" + (int) (sp(ChanSettings.fontSize.get()) * 1.2) + "px" +
                "&fcolor=" + getRGBColorIntString(ThemeHelper.getTheme().textColorInt) +
                "&mode=0" +
                "&out=1" +
                "&preamble=\\usepackage{amsmath}\r\n\\usepackage{amsfonts}\r\n\\usepackage{amssymb}" +
                "&rnd=" + Random.Default.nextDouble() * 100 +
                "&remhost=quicklatex.com";
        //@formatter:on
        return new Request.Builder()
                .url("https://www.quicklatex.com/latex3.f")
                .post(RequestBody.create(postBody, null))
                .build();
    }

    private Triple<Call, Callback, Runnable> generateMathSpanCalls(
            SpannableStringBuilder comment, @NonNull HttpUrl imageUrl, String rawMath
    ) {
        // execute immediately, so that the invalidate function is called when all embeds are done
        // that means that we can't enqueue this request
        return NetUtils.makeBitmapRequest(imageUrl, new NetUtilsClasses.BitmapResult() {
            @Override
            public void onBitmapFailure(
                    @NonNull HttpUrl source, Exception e
            ) {} // don't do any replacements with failed bitmaps, leave it as-is so it's somewhat still readable

            @Override
            public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                int startIndex = 0;
                while (true) {
                    synchronized (comment) {
                        startIndex = TextUtils.indexOf(comment, rawMath, startIndex);
                        if (startIndex < 0) break;
                        CharSequence replacement = span(" ", new ImageSpan(getAppContext(), bitmap));
                        comment.replace(startIndex, startIndex + rawMath.length(), replacement);
                        startIndex = startIndex + replacement.length();
                    }
                }
            }
        }, 0, 0, -1, null, false, false);
    }
}
