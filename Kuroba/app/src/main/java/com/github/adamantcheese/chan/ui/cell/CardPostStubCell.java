package com.github.adamantcheese.chan.ui.cell;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

public class CardPostStubCell
        extends CardView
        implements PostCellInterface {
    private Post post;
    private PostCellInterface.PostCellCallback callback;

    private TextView title;
    private ImageView options;

    public CardPostStubCell(@NonNull Context context) {
        super(context);
    }

    public CardPostStubCell(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CardPostStubCell(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        title = findViewById(R.id.title);
        options = findViewById(R.id.options);
        setCompact(false);
    }

    private void showOptions(
            View anchor,
            List<FloatingMenuItem<PostOptions>> items,
            List<FloatingMenuItem<PostOptions>> extraItems,
            Object extraOption
    ) {
        FloatingMenu<PostOptions> menu = new FloatingMenu<>(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.ClickCallback<PostOptions>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<PostOptions> menu, FloatingMenuItem<PostOptions> item) {
                if (item.getId() == extraOption) {
                    showOptions(anchor, extraItems, null, null);
                }

                callback.onPostOptionClicked(anchor, post, item.getId(), false);
            }
        });
        menu.show();
    }

    @Override
    public void setPost(
            Post post, PostCellCallback callback, boolean inPopup, boolean highlighted, boolean compact, Theme theme
    ) {
        this.post = post;
        this.callback = callback;

        // Spans are stripped here, to better distinguish a stub post
        if (!TextUtils.isEmpty(post.subjectSpan)) {
            title.setText(post.subjectSpan.toString());
        } else {
            title.setText(post.comment.toString());
        }

        setCompact(compact);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<PostOptions>> items = new ArrayList<>();
            List<FloatingMenuItem<PostOptions>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });
    }

    @Override
    public void unsetPost() {
        post = null;
        callback = null;
        title.setText(null);
        setCompact(false);
        options.setOnClickListener(null);
    }

    @Override
    public Post getPost() {
        return post;
    }

    @Override
    public ImageView getThumbnailView(PostImage postImage) {
        return null;
    }

    private void setCompact(boolean compact) {
        int textSizeSp = (isInEditMode() ? 15 : ChanSettings.fontSize.get()) + (compact ? -2 : 0);
        title.setTextSize(textSizeSp);
        float p = compact ? dp(getContext(), 3) : dp(getContext(), 8);

        // Same as the layout.
        updatePaddings(title, p);
        updatePaddings(options, p / 2);
    }
}
