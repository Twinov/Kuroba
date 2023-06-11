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
package com.github.adamantcheese.chan.ui.toolbar;

import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.NONE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.widget.ArrowMenuDrawable;

import java.util.ArrayList;
import java.util.List;

public class Toolbar
        extends LinearLayout
        implements ToolbarPresenter.Callback, ToolbarContainer.Callback {
    public static final int TOOLBAR_COLLAPSE_HIDE = 1000000;
    public static final int TOOLBAR_COLLAPSE_SHOW = -1000000;

    private final RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (!recyclerView.canScrollVertically(-1)) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, false);
            } else {
                processScrollCollapse(dy, false);
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager() != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
                processRecyclerViewScroll(recyclerView);
            }
        }
    };

    private final ToolbarPresenter presenter = new ToolbarPresenter(this);

    private final ImageView arrowMenuView;
    private ArrowMenuDrawable arrowMenuDrawable;
    private final Drawable overrideMenuDrawable;

    private final ToolbarContainer navigationItemContainer;

    private ToolbarCallback callback;
    private int lastScrollDeltaOffset;
    private int scrollOffset;
    private final List<ToolbarCollapseCallback> collapseCallbacks = new ArrayList<>();

    public Toolbar(Context context) {
        this(context, null);
    }

    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Toolbar);
        try {
            overrideMenuDrawable = a.getDrawable(R.styleable.Toolbar_menuDrawable);
        } finally {
            a.recycle();
        }

        LayoutInflater.from(context).inflate(R.layout.layout_toolbar, this, true);

        arrowMenuView = findViewById(R.id.arrow_menu_view);
        navigationItemContainer = findViewById(R.id.toolbar_container);

        setBackgroundColor(getAttrColor(context, R.attr.colorPrimary));
        setElevation(dp(context, 4));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        arrowMenuView.setOnClickListener(v -> {
            if (callback == null) return;
            callback.onMenuOrBackClicked(arrowMenuDrawable.getProgress() == 1f);
        });

        arrowMenuDrawable = new ArrowMenuDrawable(getContext());
        arrowMenuView.setImageDrawable(arrowMenuDrawable);

        navigationItemContainer.setCallback(this);
        navigationItemContainer.setArrowMenu(arrowMenuDrawable);

        if (overrideMenuDrawable != null) {
            arrowMenuView.setImageDrawable(overrideMenuDrawable);
        }

        if (isInEditMode()) {
            NavigationItem editItem = new NavigationItem();
            editItem.hasBack = false;
            editItem.title = "/test/";
            editItem.subtitle = "Test Board";
            editItem
                    .buildMenu()
                    .withItem(getContext().getDrawable(R.drawable.ic_fluent_search_24_filled), null)
                    .withItem(getContext().getDrawable(R.drawable.animated_refresh_icon), null)
                    .withOverflow(getContext())
                    .build()
                    .build();
            setNavigationItem(NONE, editItem);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isTransitioning() || super.dispatchTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    public int getToolbarHeight() {
        return getHeight() == 0 ? getLayoutParams().height : getHeight();
    }

    public void addCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.add(callback);
    }

    public void removeCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.remove(callback);
    }

    public void processScrollCollapse(int offset, boolean animated) {
        lastScrollDeltaOffset = offset;
        setCollapse(offset, animated);
    }

    public void collapseShow(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animated);
    }

    public void setCollapse(int offset, boolean animated) {
        scrollOffset += offset;
        scrollOffset = Math.max(0, Math.min(getHeight(), scrollOffset));

        if (animated) {
            Interpolator slowdown = new DecelerateInterpolator(2f);
            animate().translationY(-scrollOffset).setInterpolator(slowdown).start();

            boolean collapse = scrollOffset > 0;
            for (ToolbarCollapseCallback c : collapseCallbacks) {
                c.onCollapseAnimation(collapse);
            }
        } else {
            animate().cancel();
            setTranslationY(-scrollOffset);

            for (ToolbarCollapseCallback c : collapseCallbacks) {
                c.onCollapseTranslation(scrollOffset / (float) getHeight());
            }
        }
    }

    public void attachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(recyclerViewOnScrollListener);
    }

    public void detachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.removeOnScrollListener(recyclerViewOnScrollListener);
    }

    public void checkToolbarCollapseState(RecyclerView recyclerView) {
        processRecyclerViewScroll(recyclerView);
    }

    private void processRecyclerViewScroll(RecyclerView recyclerView) {
        View positionZero = recyclerView.getLayoutManager().findViewByPosition(0);
        boolean allowHide = positionZero == null || positionZero.getTop() < 0;
        if (allowHide || lastScrollDeltaOffset <= 0) {
            setCollapse(lastScrollDeltaOffset <= 0 ? TOOLBAR_COLLAPSE_SHOW : TOOLBAR_COLLAPSE_HIDE, true);
        } else {
            setCollapse(TOOLBAR_COLLAPSE_SHOW, true);
        }
    }

    public void openSearch() {
        presenter.openSearch();
    }

    public boolean closeSearch() {
        return presenter.closeSearch();
    }

    public boolean isSearchOpen() {
        return presenter.isSearchOpen();
    }

    public boolean isTransitioning() {
        return navigationItemContainer.isTransitioning();
    }

    public void setNavigationItem(
            final ToolbarPresenter.AnimationStyle style, final NavigationItem item
    ) {
        presenter.set(item, style);
    }

    public void beginTransition(NavigationItem newItem) {
        presenter.startTransition(newItem);
    }

    public void transitionProgress(float progress) {
        presenter.setTransitionProgress(progress);
    }

    public void finishTransition(boolean completed) {
        presenter.stopTransition(completed);
    }

    public void setCallback(ToolbarCallback callback) {
        this.callback = callback;
    }

    public ArrowMenuDrawable getArrowMenuDrawable() {
        return arrowMenuDrawable;
    }

    public void updateTitle(NavigationItem navigationItem) {
        presenter.update(navigationItem);
    }

    @Override
    public void showForNavigationItem(NavigationItem item, ToolbarPresenter.AnimationStyle animation) {
        navigationItemContainer.set(item, animation);
    }

    @Override
    public void containerStartTransition(NavigationItem item, ToolbarPresenter.TransitionAnimationStyle animation) {
        navigationItemContainer.startTransition(item, animation);
    }

    @Override
    public void containerStopTransition(boolean didComplete) {
        navigationItemContainer.stopTransition(didComplete);
    }

    @Override
    public void containerSetTransitionProgress(float progress) {
        navigationItemContainer.setTransitionProgress(progress);
    }

    @Override
    public void searchInput(String input) {
        presenter.searchInput(input);
    }

    @Override
    public void onClearPressedWhenEmpty() {
        if (callback == null) return;
        callback.onClearPressedWhenEmpty();
    }

    @Override
    public void onNavItemSet(NavigationItem item) {
        if (callback == null) return;
        callback.onNavItemSet(item);
    }

    @Override
    public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
        if (callback == null) return;
        callback.onSearchVisibilityChanged(item, visible);

        if (!visible) {
            hideKeyboard(navigationItemContainer);
        }
    }

    @Override
    public void onSearchInput(NavigationItem item, String input) {
        if (callback == null) return;
        callback.onSearchEntered(item, input);
    }

    @Override
    public void updateViewForItem(NavigationItem item) {
        navigationItemContainer.update(item);
    }

    public interface ToolbarCallback {
        void onMenuOrBackClicked(boolean isArrow);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        void onSearchEntered(NavigationItem item, String entered);

        void onClearPressedWhenEmpty();

        void onNavItemSet(NavigationItem item);
    }

    public interface ToolbarCollapseCallback {
        void onCollapseTranslation(float offset);

        void onCollapseAnimation(boolean collapse);
    }
}
