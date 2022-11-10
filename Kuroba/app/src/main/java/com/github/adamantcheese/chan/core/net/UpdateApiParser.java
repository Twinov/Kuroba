/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.net;

import static com.github.adamantcheese.chan.BuildConfig.*;
import static com.github.adamantcheese.chan.core.settings.PersistableChanState.previousDevHash;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.text.style.URLSpan;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import com.github.adamantcheese.chan.core.net.UpdateApiParser.UpdateApiResponse;
import com.github.adamantcheese.chan.features.html_styling.StyledHtml;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class UpdateApiParser
        implements NetUtilsClasses.Converter<UpdateApiResponse, JsonReader> {
    private final String versionPatternString = "v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})";
    private final Pattern RELEASE_PATTERN = Pattern.compile(versionPatternString);
    private final Pattern DEV_PATTERN = Pattern.compile(versionPatternString + "-(.*)");

    @Override
    public UpdateApiResponse convert(JsonReader reader)
            throws Exception {
        UpdateApiResponse response = new UpdateApiResponse();
        if (DEV_BUILD) {
            response.body = span(
                    "New dev build; see commits!",
                    new URLSpan(GITHUB_ENDPOINT + "/compare/" + previousDevHash.get() + "..." + COMMIT_HASH)
            );
        } else {
            response.body =
                    "Changelog:\nSee the release on Github for details!\nYour Android API is too low to properly render the changelog from the site.";
        }

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name":
                    response.versionCodeString = reader.nextString();
                    Matcher versionMatcher =
                            (DEV_BUILD ? DEV_PATTERN : RELEASE_PATTERN).matcher(response.versionCodeString);
                    if (versionMatcher.matches()) {
                        try {
                            //@formatter:off
                            //noinspection PointlessArithmeticExpression,ConstantConditions
                            response.versionCode =
                                    10000 * Integer.parseInt(versionMatcher.group(1)) +
                                    100   * Integer.parseInt(versionMatcher.group(2)) +
                                    1     * Integer.parseInt(versionMatcher.group(3));
                            response.apkURL =
                                    HttpUrl.get((DEV_BUILD ? DEV_GITHUB_ENDPOINT : GITHUB_ENDPOINT) +
                                    "/releases/download/" + response.versionCodeString + "/" + APP_LABEL +
                                    (DEV_BUILD ? "-" + versionMatcher.group(4) : "") + ".apk");
                            //@formatter:on
                            break;
                        } catch (Exception e) {
                            throw new IOException("Error processing tag_name; apkUrl likely failed to be valid", e);
                        }
                    } else {
                        throw new MalformedJsonException("Error processing tag_name; version code string is not valid.");
                    }
                case "name":
                    if (!DEV_BUILD) {
                        response.updateTitle = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                    break;
                case "body":
                    if (DEV_BUILD) {
                        response.commitHash = reader.nextString().trim();
                    } else {
                        Node updateLog = Parser.builder().build().parse(reader.nextString());
                        response.body = StyledHtml.fromHtml("Changelog:\r\n" + HtmlRenderer
                                .builder()
                                .build()
                                .render(updateLog), null);
                    }
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return response;
    }

    public static class UpdateApiResponse {
        public String commitHash = "";
        public int versionCode = 0;
        public String versionCodeString;
        public String updateTitle = "";
        public HttpUrl apkURL;
        public CharSequence body;
    }
}
