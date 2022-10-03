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
package com.github.adamantcheese.chan.ui.controller.settings;

import static androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL;
import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.COLOR;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.PostsOrder.BUMP_ORDER;
import static com.github.adamantcheese.chan.ui.theme.ThemeHelper.createTheme;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.NONE;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.TEST_POST_ICON_URL;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.TEST_POST_IMAGE_URL;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.*;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.controller.ImageViewerController;
import com.github.adamantcheese.chan.ui.controller.ImageViewerNavigationController;
import com.github.adamantcheese.chan.ui.text.post_linkables.*;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.*;
import com.github.adamantcheese.chan.ui.view.*;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.*;

public class ThemeSettingsController
        extends Controller {

    private final Loadable dummyLoadable = Loadable.dummyLoadable().clone();

    {
        dummyLoadable.mode = Loadable.Mode.THREAD;
        dummyLoadable.lastViewed = 234567890;
    }

    private final PostCell.PostCellCallback dummyPostCallback = new PostCell.PostCellCallback() {
        @Override
        public Loadable getLoadable() {
            return dummyLoadable;
        }

        @Override
        public String getSearchQuery() {
            return "search highlighting";
        }

        @Override
        public void onPostClicked(Post post) {
        }

        @Override
        public void onPostDoubleClicked(Post post) {
        }

        @Override
        public void onThumbnailClicked(PostImage postImage, ImageView thumbnail) {
            ImageViewerNavigationController imagerViewer = new ImageViewerNavigationController(context);
            presentController(imagerViewer, false);
            imagerViewer.showImages(Collections.singletonList(postImage),
                    0,
                    dummyLoadable,
                    new ImageViewerController.ImageViewerCallback() {
                        @Override
                        public ImageView getPreviewImageTransitionView(PostImage postImage) {
                            return thumbnail;
                        }

                        @Override
                        public void scrollToImage(PostImage postImage) {}

                        @Override
                        public Post getPostForPostImage(PostImage postImage) {
                            return null;
                        }
                    }
            );
        }

        @Override
        public void onShowPostReplies(Post post) {
        }

        @Override
        public Object onPopulatePostOptions(
                Post post, List<FloatingMenuItem<PostOptions>> menu, List<FloatingMenuItem<PostOptions>> extraMenu
        ) {
            menu.add(new FloatingMenuItem<>(PostOptions.POST_OPTION_INFO, "Option"));
            return 0;
        }

        @Override
        public void onPostOptionClicked(View anchor, Post post, PostOptions id, boolean inPopup) {
            if (id == PostOptions.POST_OPTION_INFO) {
                showToast(context, "Menu option test.");
            }
        }

        @Override
        public void onPostLinkableClicked(Post post, PostLinkable<?> linkable) {
            if (linkable instanceof QuoteLinkable) {
                showToast(context, "Clicked on quote " + linkable.value + "!");
            } else if (linkable instanceof ParserLinkLinkable || linkable instanceof EmbedderLinkLinkable) {
                if (ChanSettings.openLinkBrowser.get()) {
                    AndroidUtils.openLink((String) linkable.value);
                } else {
                    openLinkInBrowser(context, (String) linkable.value);
                }
            }
        }

        @Override
        public void onPostNoClicked(Post post) {
        }

        @Override
        public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        }
    };

    private CoordinatorLayout wrapper;
    private ViewPager2 pager;
    private FloatingActionButton done;

    private boolean currentDayNight;

    public ThemeSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationController.getToolbar().updateViewForItem(navigation);
        navigation.setTitle(R.string.settings_screen_theme);
        navigation.swipeable = false;
        NavigationItem.MenuBuilder builder =
                navigation.buildMenu().withItem(R.drawable.ic_fluent_question_circle_24_regular, this::helpClicked);
        if (isAndroid10()) {
            builder.withItem(ThemeHelper.isNightTheme
                    ? R.drawable.ic_fluent_weather_moon_24_filled
                    : R.drawable.ic_fluent_weather_sunny_24_filled, this::dayNightToggle);
        }
        builder.build();
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_theme, null);

        Theme currentTheme = ThemeHelper.getTheme();
        // restore if the user pressed back
        currentDayNight = ThemeHelper.isNightTheme;

        wrapper = view.findViewById(R.id.wrapper);
        pager = view.findViewById(R.id.pager);
        done = view.findViewById(R.id.add);
        done.setOnClickListener(v -> saveTheme());

        // pager setup
        pager.setOffscreenPageLimit(1);
        // display on the sides
        pager.setPageTransformer((page, position) -> {
            float offset = position * -(2 * dp(context, 6) + dp(context, 6));
            if (pager.getOrientation() == ORIENTATION_HORIZONTAL) {
                if (ViewCompat.getLayoutDirection(pager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.setTranslationX(-offset);
                } else {
                    page.setTranslationX(offset);
                }
            } else {
                page.setTranslationY(offset);
            }
        });
        // update done and background color when a new theme is selected
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Theme currentTheme = getViewedTheme();
                done.setBackgroundTintList(ColorStateList.valueOf(currentTheme.accentColorInt));
                wrapper.setBackgroundColor(currentTheme.backColorInt);
            }
        });

        updateAdapter(currentTheme);
    }

    @Override
    public boolean onBack() {
        ThemeHelper.resetThemes();
        ThemeHelper.isNightTheme = currentDayNight;
        return super.onBack();
    }

    private Theme getViewedTheme() {
        return ThemeHelper.themes.get(pager.getCurrentItem());
    }

    private void saveTheme() {
        if (ThemeHelper.isNightTheme) {
            ChanSettings.themeNight.setSync(getViewedTheme().toString());
        } else {
            ChanSettings.themeDay.setSync(getViewedTheme().toString());
        }
        ((StartActivity) context).restartApp();
    }

    private void helpClicked(ToolbarMenuItem item) {
        final AlertDialog dialog = getDefaultAlertBuilder(context)
                .setTitle("Help")
                .setMessage(R.string.setting_theme_explanation)
                .setPositiveButton("Close", null)
                .show();
        dialog.setCanceledOnTouchOutside(true);
    }

    private void dayNightToggle(ToolbarMenuItem item) {
        //reset theme choices
        ThemeHelper.resetThemes();

        //toggle toolbar item
        if (ThemeHelper.isNightTheme) {
            item.setImage(R.drawable.ic_fluent_weather_sunny_24_filled);
            ThemeHelper.isNightTheme = false;
        } else {
            item.setImage(R.drawable.ic_fluent_weather_moon_24_filled);
            ThemeHelper.isNightTheme = true;
        }
        navigationController.getToolbar().updateViewForItem(navigation);

        updateAdapter(ThemeHelper.getTheme());
        //update button color manually, in case onPageSelected isn't called
        done.setBackgroundTintList(ColorStateList.valueOf(ThemeHelper.getTheme().accentColorInt));
        wrapper.setBackgroundColor(getAttrColor(ThemeHelper.getTheme().resValue, R.attr.backcolor));
    }

    private void showAccentColorPicker() {
        List<FloatingMenuItem<MaterialColorStyle>> items = new ArrayList<>();
        FloatingMenuItem<MaterialColorStyle> selected = null;
        for (MaterialColorStyle color : MaterialColorStyle.values()) {
            FloatingMenuItem<MaterialColorStyle> floatingMenuItem = new FloatingMenuItem<>(color, color.prettyName());
            items.add(floatingMenuItem);
            if (color == getViewedTheme().getAccentColor()) {
                selected = floatingMenuItem;
            }
        }

        FloatingMenu<MaterialColorStyle> menu = getColorsMenu(items, selected, done, true);
        menu.setCallback(new FloatingMenu.ClickCallback<MaterialColorStyle>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<MaterialColorStyle> menu, FloatingMenuItem<MaterialColorStyle> item
            ) {
                Theme currentTheme = getViewedTheme();
                currentTheme.setAccentColor(item.getId());
                done.setBackgroundTintList(ColorStateList.valueOf(ThemeHelper.getTheme().accentColorInt));
                updateAdapter(currentTheme);
            }
        });
        menu.setPopupHeight((int) dp(context, 300));
        menu.show();
    }

    private FloatingMenu<MaterialColorStyle> getColorsMenu(
            List<FloatingMenuItem<MaterialColorStyle>> items,
            FloatingMenuItem<MaterialColorStyle> selected,
            View anchor,
            boolean useAccentColors
    ) {
        FloatingMenu<MaterialColorStyle> menu = new FloatingMenu<>(context, anchor, items);
        menu.setAnchorGravity(Gravity.CENTER, 0, 0);
        menu.setAdapter(new ColorsAdapter(items, useAccentColors));
        menu.setSelectedItem(selected);
        return menu;
    }

    private void updateAdapter(Theme currentTheme) {
        int i;
        for (i = 0; i < ThemeHelper.themes.size(); i++) {
            Theme theme = ThemeHelper.themes.get(i);
            if (theme.name.equals(currentTheme.name)) {
                theme.setPrimaryColor(currentTheme.getPrimaryColor());
                theme.setAccentColor(currentTheme.getAccentColor());
                break;
            }
        }
        pager.setAdapter(new ThemePostsAdapter());
        pager.setCurrentItem(i, false);
    }

    private class ThemePostsAdapter
            extends RecyclerView.Adapter<ThemePostsAdapter.ThemePreviewHolder> {
        public ThemePostsAdapter() {
        }

        // NOTE
        // This adapter is a bit weird because we need to change contexts a lot
        // So anytime we do something that changes the theme, we need to refresh the whole adapter and set the current item
        // updateAdapter takes care of that
        @NonNull
        @Override
        public ThemePostsAdapter.ThemePreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Theme theme = ThemeHelper.themes.get(viewType);
            Context themeContext = new ContextThemeWrapper(context, createTheme(context, theme));
            return new ThemePreviewHolder(theme,
                    LayoutInflater.from(themeContext).inflate(R.layout.layout_theme_preview, parent, false)
            );
        }

        @Override
        public int getItemCount() {
            return ThemeHelper.themes.size();
        }

        @Override
        public int getItemViewType(int position) {
            // in order to index into ThemeHelper's themes in onCreateViewHolder, we just return the position here
            return position;
        }

        @Override
        public void onViewRecycled(@NonNull ThemePreviewHolder holder) {
            holder.recyclerView.setAdapter(null);
        }

        @Override
        public void onBindViewHolder(@NonNull ThemePostsAdapter.ThemePreviewHolder holder, int position) {
            PostParser postParser =
                    new PostParser(new ChanCommentAction()).withOverrideFilters(generateFilters(holder.itemView.getContext()));
            List<Post> posts = new ArrayList<>();
            for (Post.Builder builder : generatePosts()) {
                posts.add(postParser.parse(builder, holder.theme, new PostParser.PostParserCallback() {
                    @Override
                    public boolean isRemoved(int postNo) {
                        return postNo == 666;
                    }
                }));
            }
            posts.get(0).repliesFrom.add(posts.get(2).no); // add reply to first post pointing to last post
            posts.get(3).deleted = true; // mark as deleted
            ChanThread thread = new ChanThread(dummyLoadable, posts);

            for (Post p : thread.getPosts()) {
                QuoteLinkable[] linkables = p.getQuoteLinkables();
                for (QuoteLinkable linkable : linkables) {
                    linkable.setMarkedNo(linkables.length > 1 ? 123456789 : -1);
                }
            }

            PostAdapter adapter = new PostAdapter(holder.recyclerView, post -> {
            }, dummyPostCallback, new ThreadStatusCell.Callback() {
                @Override
                public long getTimeUntilLoadMore() {
                    return 0;
                }

                @Override
                public boolean isWatching() {
                    return false;
                }

                @Override
                public ChanThread getChanThread() {
                    return thread;
                }

                @Override
                public void onListStatusClicked() {}
            }, holder.theme) {
                @Override
                public boolean showStatusView() {
                    return false;
                }

                @Override
                public boolean allowsDashedUnderlines() {
                    return true;
                }
            };
            adapter.setThread(thread, new PostsFilter(BUMP_ORDER, null));
            adapter.highlightPostNo(posts.get(2).no); // highlight third post
            holder.recyclerView.setAdapter(adapter);

            final View.OnClickListener colorClick = v -> {
                List<FloatingMenuItem<MaterialColorStyle>> items = new ArrayList<>();
                FloatingMenuItem<MaterialColorStyle> selected = null;
                for (MaterialColorStyle color : MaterialColorStyle.values()) {
                    FloatingMenuItem<MaterialColorStyle> floatingMenuItem =
                            new FloatingMenuItem<>(color, color.prettyName());
                    items.add(floatingMenuItem);
                    if (color == holder.theme.getPrimaryColor()) {
                        selected = floatingMenuItem;
                    }
                }

                FloatingMenu<MaterialColorStyle> menu = getColorsMenu(items, selected, holder.toolbar, false);
                menu.setCallback(new FloatingMenu.ClickCallback<MaterialColorStyle>() {
                    @Override
                    public void onFloatingMenuItemClicked(
                            FloatingMenu<MaterialColorStyle> menu, FloatingMenuItem<MaterialColorStyle> item
                    ) {
                        holder.theme.setPrimaryColor(item.getId());
                        holder.toolbar.setBackgroundColor(holder.theme.colorPrimaryColorInt);
                    }
                });
                menu.setPopupHeight((int) dp(context, 300));
                menu.show();
            };
            holder.toolbar.setCallback(new Toolbar.ToolbarCallback() {
                @Override
                public void onMenuOrBackClicked(boolean isArrow) {
                    colorClick.onClick(holder.toolbar);
                }

                @Override
                public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
                }

                @Override
                public void onSearchEntered(NavigationItem item, String entered) {
                }

                @Override
                public void onNavItemSet(NavigationItem item) {
                }

                @Override
                public void onClearPressedWhenEmpty() {
                }
            });
            final NavigationItem item = new NavigationItem();
            item.title = holder.theme.name;
            item.hasBack = false;
            item
                    .buildMenu()
                    .withItem(R.drawable.ic_fluent_highlight_20_filled, (v) -> showAccentColorPicker())
                    .withOverflow()
                    .withSubItem(R.string.test_snackbar, () -> {
                        Snackbar test = AndroidUtils.buildCommonSnackbar(view,
                                holder.itemView.getContext().getString(R.string.test_snackbar)
                        );
                        test.setAction(R.string.cancel, v -> test.dismiss());
                        test.show();
                    })
                    .withSubItem(R.string.test_popup, () -> {
                        AlertDialog test = getDefaultAlertBuilder(holder.itemView.getContext())
                                .setMessage(R.string.test_popup)
                                .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss())
                                .create();
                        test.setCanceledOnTouchOutside(true);
                        test.show();
                    })
                    .build()
                    .build();
            holder.toolbar.setNavigationItem(NONE, item);
            holder.toolbar.setOnClickListener(colorClick);
        }

        @NonNull
        private List<Post.Builder> generatePosts() {
            Post.Builder builder1 = new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(123456789)
                    .opId(123456789)
                    .posterId("TeStId++")
                    .idColor(0xFF317CD3)
                    .op(true)
                    .replies(1)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(60)))
                    .subject("Lorem ipsum")
                    .comment("<span class=\"deadlink\">&gt;&gt;987654321</span><br>"
                            + "http://example.com/<br>"
                            + "This text is normally colored. <span class=\"spoiler\">This text is spoilered.</span><br>"
                            + "<span class=\"quote\">&gt;This text is inline quoted (greentext).</span><br>"
                            + "<span class=\"quote\">&gt;This is a inline quoted quote. "
                            + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a></span><br>"
                            + "<span class=\"quote\">&gt;This is a inline quoted quote that is marked. "
                            + "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a></span><br>"
                            + "<span class=\"spoiler\">This is a spoilered link http://example.com/</span>");

            Post.Builder builder2 = new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(234567890)
                    .opId(123456789)
                    .posterId("TeStId2+")
                    .addHttpIcon(new PostHttpIcon(SiteEndpoints.IconType.BOARD_FLAG,
                            TEST_POST_ICON_URL,
                            new PassthroughBitmapResult(),
                            "test",
                            "Test icon"
                    ))
                    .idColor(0xFF471D0A)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(30)))
                    .comment(
                            "This is a spacer post for seeing the divider color; below are links for embed testing:<br>"
                                    + "https://www.youtube.com/watch?v=dQw4w9WgXcQ<br>"
                                    + "<span class=\"spoiler\">https://www.youtube.com/watch?v=dQw4w9WgXcQ</span>");

            Post.Builder builder3 = new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(345678901)
                    .opId(123456789)
                    .name("W.T. Snacks")
                    .tripcode("!TcT.PTG1.2")
                    .posterId("TeStId3+")
                    .idColor(0xFFEAE189)
                    .moderatorCapcode("Mod")
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(15)))
                    .comment(
                            "<a href=\"#p123456789\" class=\"quotelink\">&gt;&gt;123456789</a> This link is marked.<br>"
                                    + "<a href=\"#p111111111\" class=\"quotelink\">&gt;&gt;111111111</a><br>"
                                    + "This post is highlighted.<br>"
                                    + "<span class=\"spoiler\">This text is spoilered in a highlighted post.</span><br>"
                                    + "This text has search highlighting applied.")
                    .images(Collections.singletonList(new PostImage.Builder()
                            .imageUrl(TEST_POST_IMAGE_URL)
                            .thumbnailUrl(TEST_POST_IMAGE_URL)
                            .filename("new_icon_512")
                            .extension("png")
                            .build()));

            Post.Builder builder4 = new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(666)
                    .opId(123456789)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(10)))
                    .comment("This post is deleted!<br>"
                            + "&verbar;&verbar;This line has extra spoiler characters around it.&verbar;&verbar;");

            Post.Builder builder5 = new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(999)
                    .opId(123456789)
                    .setUnixTimestampSeconds(MILLISECONDS.toSeconds(System.currentTimeMillis() - MINUTES.toMillis(5)))
                    .comment(
                            "<a href=\"#p666\" class=\"quotelink\">&gt;&gt;666</a> This post is replying to a post that is deleted!<br>"
                                    + "Below is an image link to test out image embedding.<br>"
                                    + "https://picsum.photos/512.jpg");

            return Arrays.asList(builder1, builder2, builder3, builder4, builder5);
        }

        private Filters generateFilters(Context context) {
            Filter filter1 = new Filter(true,
                    FilterType.SUBJECT.flag | FilterType.COMMENT.flag,
                    "testing",
                    "",
                    true,
                    "",
                    COLOR.ordinal(),
                    getAttrColor(context, R.attr.colorAccent) & 0x7FFFFFFF,
                    false,
                    0,
                    false,
                    false,
                    ""
            );
            Filter filter2 = new Filter(true,
                    FilterType.SUBJECT.flag | FilterType.COMMENT.flag,
                    "spacer",
                    "",
                    true,
                    "",
                    COLOR.ordinal(),
                    getAttrColor(context, R.attr.post_quote_color) & 0x7FFFFFFF,
                    false,
                    1,
                    false,
                    false,
                    ""
            );
            return new Filters(filter1, filter2);
        }

        private class ThemePreviewHolder
                extends RecyclerView.ViewHolder {
            private final Theme theme;
            private final RecyclerView recyclerView;
            private final Toolbar toolbar;

            public ThemePreviewHolder(Theme theme, @NonNull View itemView) {
                super(itemView);
                this.theme = theme;
                recyclerView = itemView.findViewById(R.id.posts_recycler);
                recyclerView.addItemDecoration(FastScrollerHelper.create(recyclerView));
                toolbar = itemView.findViewById(R.id.theme_toolbar);
            }
        }
    }

    private static class ColorsAdapter
            extends BaseAdapter {
        private final List<FloatingMenuItem<MaterialColorStyle>> colors;
        private final boolean useAccentColors;

        public ColorsAdapter(List<FloatingMenuItem<MaterialColorStyle>> items, boolean useAccentColors) {
            this.colors = items;
            this.useAccentColors = useAccentColors;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.toolbar_menu_item, parent, false));
            textView.setText(getItem(position));

            MaterialColorStyle color = colors.get(position).getId();

            int colorForItem = useAccentColors
                    ? getAttrColor(color.accentStyleId, R.attr.colorAccent)
                    : getAttrColor(color.primaryColorStyleId, R.attr.colorPrimary);
            textView.setBackgroundColor(colorForItem);
            textView.setTextColor(getContrastColor(colorForItem));

            return textView;
        }

        @Override
        public int getCount() {
            return colors.size();
        }

        @Override
        public String getItem(int position) {
            return colors.get(position).getText();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
