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
package com.github.adamantcheese.chan.ui.helper;

import static com.github.adamantcheese.chan.utils.StringUtils.span;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.text.CenteringImageSpan;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostHelper {
    public static String getTitle(@Nullable Post post, Loadable loadable) {
        if (post != null) {
            if (!TextUtils.isEmpty(post.subject)) {
                return post.subject.toString();
            } else if (!TextUtils.isEmpty(post.comment)) {
                return post.comment.subSequence(0, Math.min(post.comment.length(), 200)) + "";
            } else {
                return "/" + post.boardCode + "/" + post.no;
            }
        } else if (loadable != null) {
            return "/" + loadable.boardCode + "/" + (loadable.isThreadMode() ? loadable.no : "");
        } else {
            return "";
        }
    }

    private static final DateFormat dateFormat =
            SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault());
    private static final Date tmpDate = new Date();

    public static String getLocalDate(Post post) {
        tmpDate.setTime(SECONDS.toMillis(post.time));
        return dateFormat.format(tmpDate);
    }
}
