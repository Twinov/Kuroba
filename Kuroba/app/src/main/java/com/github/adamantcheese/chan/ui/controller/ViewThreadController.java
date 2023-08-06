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
package com.github.adamantcheese.chan.ui.controller;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAprilFoolsDay;

import android.content.Context;
import android.view.View;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.*;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.*;
import com.github.k1rakishou.fsaf.FileManager;
import com.skydoves.balloon.*;

import org.greenrobot.eventbus.Subscribe;

import java.util.*;

import javax.inject.Inject;

public class ViewThreadController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback, ArchivesLayout.Callback, ToolbarMenuItem.OverflowMenuCallback {

    private enum MenuId {
        ALBUM,
        PIN
    }

    private enum OverflowMenuId {
        REPLY,
        VIEW_ARCHIVE,
        VIEW_REMOVED
    }

    @Inject
    WatchManager watchManager;
    @Inject
    FileManager fileManager;

    private Loadable loadable;

    //pairs of the current thread loadable and the thread we're going to's hashcode
    private final Deque<Pair<Loadable, Integer>> threadFollowerpool = new ArrayDeque<>();

    private FloatingMenu<ToolbarMenuSubItem> floatingMenu;

    public ViewThreadController(Context context, Loadable loadable) {
        super(context);
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);
        threadLayout.getPresenter().setOrder(ChanSettings.threadOrder.get());
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigation.hasDrawer = true;

        buildMenu();
        loadThread(loadable);
    }

    protected void buildMenu() {
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();

        if (!ChanSettings.textOnly.get()) {
            menuBuilder.withItem(MenuId.ALBUM, R.drawable.ic_fluent_image_24_filled, this::albumClicked);
        }
        menuBuilder.withItem(MenuId.PIN, R.drawable.ic_fluent_bookmark_24_regular, this::pinClicked);

        NavigationItem.MenuOverflowBuilder menuOverflowBuilder = menuBuilder.withOverflow(this);

        if (!ChanSettings.enableReplyFab.get()) {
            menuOverflowBuilder.withSubItem(OverflowMenuId.REPLY,
                    isAprilFoolsDay() ? R.string.action_reply_fools : R.string.action_reply,
                    () -> threadLayout.openReply(true)
            );
        }

        menuOverflowBuilder
                .withSubItem(R.string.action_search,
                        () -> ((ToolbarNavigationController) navigationController).showSearch()
                )
                .withSubItem(R.string.action_reload, () -> threadLayout.getPresenter().requestData());
        if (loadable.site instanceof Chan4) { //archives are 4chan only
            menuOverflowBuilder.withSubItem(OverflowMenuId.VIEW_ARCHIVE,
                    R.string.thread_view_external_archive,
                    () -> threadLayout.getPresenter().showArchives(loadable, loadable.no)
            );
        }
        menuOverflowBuilder
                .withSubItem(OverflowMenuId.VIEW_REMOVED,
                        R.string.view_removed_posts,
                        () -> threadLayout.getPresenter().showRemovedPostsDialog()
                )
                .withSubItem(R.string.view_my_posts, this::showYourPosts)
                .withSubItem(R.string.action_sort, () -> handleSorting(null))
                .withSubItem(R.string.action_open_browser, () -> handleShareAndOpenInBrowser(false))
                .withSubItem(R.string.action_share, () -> handleShareAndOpenInBrowser(true))
                .withSubItem(R.string.action_scroll_to_top, () -> threadLayout.scrollTo(0, false))
                .withSubItem(R.string.action_scroll_to_bottom, () -> threadLayout.scrollTo(-1, false))
                .withSubItem(R.string.action_scroll_to_new, () -> threadLayout.getPresenter().onNewPostsViewClicked());

        menuOverflowBuilder.build().build();
    }

    private void albumClicked(ToolbarMenuItem item) {
        dismissFloatingMenu();
        threadLayout.getPresenter().showAlbum();
    }

    private void pinClicked(ToolbarMenuItem item) {
        dismissFloatingMenu();

        Pin pin = watchManager.getPinByLoadable(loadable);
        if (pin == null) {
            ChanThread thread = threadLayout.getPresenter().getChanThread();
            if (thread != null) {
                Post op = thread.getOp();
                loadable.thumbnailUrl = op.image() == null ? null : op.image().getThumbnailUrl();
            }
            watchManager.createPin(loadable);
        } else {
            watchManager.deletePin(pin);
        }

        setPinIconState(true);
        updateDrawerHighlighting(loadable);
    }

    public void showYourPosts() {
        if (!threadLayout.getPresenter().isBound() || threadLayout.getPresenter().getChanThread() == null) return;
        List<Post> yourPosts = new ArrayList<>();
        for (Post post : threadLayout.getPresenter().getChanThread().getPosts()) {
            if (post.isSavedReply) yourPosts.add(post);
        }

        if (yourPosts.isEmpty()) {
            showToast(context, R.string.no_saved_posts_for_current_thread);
        } else {
            threadLayout.showPostsPopup(null, yourPosts);
        }
    }

    @Override
    public void onShow() {
        super.onShow();

        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconState(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        updateDrawerHighlighting(null);
        updateLeftPaneHighlighting(null);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        setPinIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        setPinIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        setPinIconState(false);
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        setPinIconState(true);
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        if (threadLoadable.site instanceof ExternalSiteArchive && !loadable.site.equals(threadLoadable.site)) {
            showThreadInternal(threadLoadable);
        } else {
            getDefaultAlertBuilder(context)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which) -> showThreadInternal(threadLoadable))
                    .setTitle(!(threadLoadable.site instanceof ExternalSiteArchive)
                            ? R.string.open_thread_confirmation
                            : R.string.open_archived_thread_confirmation)
                    .setMessage("/" + threadLoadable.boardCode + "/" + threadLoadable.no + (threadLoadable.markedNo
                            != -1 && threadLoadable.markedNo != threadLoadable.no
                            ? " #" + threadLoadable.markedNo
                            : ""))
                    .show();
        }
    }

    private void showThreadInternal(final Loadable threadLoadable) {
        threadFollowerpool.addFirst(new Pair<>(loadable, threadLoadable.hashCode()));
        loadThread(threadLoadable);
    }

    @Override
    public void showBoard(final Loadable catalogLoadable) {
        showBoardInternal(catalogLoadable, null);
    }

    @Override
    public void showBoardAndSearch(final Loadable catalogLoadable, String search) {
        showBoardInternal(catalogLoadable, search);
    }

    private void showBoardInternal(Loadable catalogLoadable, String searchQuery) {
        if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof BrowseController) {
            //slide or phone layout
            BrowseController browseController = (BrowseController) doubleNavigationController.getLeftController();
            browseController.setBoard(catalogLoadable.board);
            browseController.searchQuery = searchQuery;
            doubleNavigationController.switchToController(true);
        } else if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof StyledToolbarNavigationController) {
            //split layout
            ((BrowseController) doubleNavigationController.getLeftController().childControllers.get(0)).setBoard(
                    catalogLoadable.board);
            if (searchQuery != null) {
                Toolbar toolbar = doubleNavigationController.getLeftController().childControllers.get(0).getToolbar();
                if (toolbar != null) {
                    toolbar.openSearch();
                    toolbar.searchInput(searchQuery);
                }
            }
        }
    }

    public void loadThread(Loadable loadable) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (!loadable.equals(presenter.getLoadable())) {
            loadThreadInternal(loadable);
        }
        updateDrawerHighlighting(loadable);
    }

    private void loadThreadInternal(Loadable loadable) {
        ThreadPresenter presenter = threadLayout.getPresenter();

        presenter.bindLoadable(loadable);
        this.loadable = loadable;

        navigation.title = loadable.title;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);

        ToolbarMenuSubItem reply = navigation.findSubItem(OverflowMenuId.REPLY);
        if (reply != null) {
            reply.enabled = loadable.site.siteFeature(Site.SiteFeature.POSTING);
        }

        ToolbarMenuSubItem archives = navigation.findSubItem(OverflowMenuId.VIEW_ARCHIVE);
        if (archives != null) {
            archives.enabled = loadable.site instanceof Chan4;
        }

        ToolbarMenuSubItem removed = navigation.findSubItem(OverflowMenuId.VIEW_REMOVED);
        if (removed != null) {
            removed.enabled = !(loadable.site instanceof ExternalSiteArchive);
        }

        ToolbarMenuItem item = navigation.findItem(MenuId.PIN);
        item.setVisible(!(loadable.site instanceof ExternalSiteArchive));
        ((ToolbarNavigationController) navigationController).toolbar.invalidate();

        setPinIconState(false);

        updateLeftPaneHighlighting(loadable);
    }

    public void updateSubtitle(CharSequence summary) {
        navigation.subtitle = ChanSettings.statusCellAsSubtitle.get() ? summary : null;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
    }

    @Override
    public void onNavItemSet() {
        if (navigation.search) return; // bit of a hack to ignore the search change
        try {
            showHints();
        } catch (Exception ignored) {}
    }

    private void showHints() {
        Balloon pinHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ThreadPinHint")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.thread_pin_hint)
                .build();
        Balloon albumHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ThreadAlbumHint")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.thread_album_hint)
                .build();
        Balloon scrollHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ThreadUpDownHint")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.thread_up_down_hint)
                .build();

        // Drawer hint
        View drawer = getDrawerRoot().findViewById(R.id.drawer);
        if (drawer == null) return;
        Balloon drawerHint = AndroidUtils
                .getBaseToolTip(context)
                .setPreferenceName("DrawerHint")
                .setArrowOrientation(ArrowOrientation.START)
                .setText("Swipe right to access bookmarks and settings")
                .build();

        // drawer hint, pin hint, album hint (if applicable), scroll hint
        Balloon chain1 = drawerHint.relayShowAlignBottom(pinHint, navigation.findItem(MenuId.PIN).getView());
        Balloon chain2 = chain1;
        if (!ChanSettings.textOnly.get()) {
            chain2 = chain1.relayShowAlignBottom(albumHint, navigation.findItem(MenuId.ALBUM).getView());
        }
        chain2.relayShowAlignBottom(scrollHint, navigation.findOverflow().getView());
        drawerHint.showAlignRight(drawer);
    }

    private void dismissFloatingMenu() {
        if (floatingMenu != null) {
            floatingMenu.dismiss();
            floatingMenu = null;
        }
    }

    @Override
    public void onShowPosts(Loadable loadable) {
        super.onShowPosts(loadable);
        setPinIconState(false);
        if (!navigation.search) {
            navigation.title = loadable.title;
            ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
        }
    }

    private void updateDrawerHighlighting(Loadable loadable) {
        Pin pin = watchManager.getPinByLoadable(loadable);
        DrawerController drawerController = getDrawerController();
        if (drawerController == null) return;
        drawerController.setPinHighlighted(pin);
    }

    private void updateLeftPaneHighlighting(Loadable loadable) {
        if (doubleNavigationController != null) {
            ThreadController threadController = null;
            Controller leftController = doubleNavigationController.getLeftController();
            if (leftController instanceof ThreadController) {
                threadController = (ThreadController) leftController;
            } else if (leftController instanceof NavigationController) {
                NavigationController leftNavigationController = (NavigationController) leftController;
                for (Controller controller : leftNavigationController.childControllers) {
                    if (controller instanceof ThreadController) {
                        threadController = (ThreadController) controller;
                        break;
                    }
                }
            }
            if (threadController != null) {
                threadController.highlightPostNo(loadable != null ? loadable.no : -1);
            }
        }
    }

    private void setPinIconState(boolean animated) {
        if (loadable == null) return;
        Pin pin = watchManager.getPinByLoadable(loadable);

        ToolbarMenuItem menuItem = navigation.findItem(MenuId.PIN);
        if (menuItem == null) {
            return;
        }

        int drawable = pin != null ? R.drawable.ic_fluent_bookmark_24_filled : R.drawable.ic_fluent_bookmark_24_regular;
        menuItem.setImage(drawable, animated);
    }

    @Override
    public void openArchive(ExternalSiteArchive externalSiteArchive, Loadable op, int postNo) {
        threadFollowerpool.addFirst(new Pair<>(loadable,
                externalSiteArchive.getArchiveLoadable(op, postNo).hashCode()
        ));
        threadLayout.getPresenter().openArchive(externalSiteArchive, op, postNo);
    }

    @Override
    public boolean threadBackPressed() {
        //clear the pool if the current thread isn't a part of this crosspost chain
        //ie a new thread is loaded and a new chain is started; this will never throw null pointer exceptions
        if (!threadFollowerpool.isEmpty() && threadFollowerpool.peekFirst().second != loadable.hashCode()) {
            threadFollowerpool.clear();
        }
        //if the thread is new, it'll be empty here, so we'll get back-to-catalog functionality
        if (threadFollowerpool.isEmpty()) {
            return false;
        }
        loadThread(threadFollowerpool.removeFirst().first);
        return true;
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.getPresenter().getPostFromPostImage(postImage);
    }

    @Override
    public void onMenuShown(FloatingMenu<ToolbarMenuSubItem> menu) {
        dismissFloatingMenu();
        floatingMenu = menu;
    }

    @Override
    public void onMenuHidden() {
    }

    private void handleSorting(@SuppressWarnings("SameParameterValue") ToolbarMenuItem item) {
        final ThreadPresenter presenter = threadLayout.getPresenter();
        List<FloatingMenuItem<PostsFilter.PostsOrder>> items = new ArrayList<>();
        for (PostsFilter.PostsOrder postsOrder : PostsFilter.PostsOrder.values()) {
            if (!postsOrder.forMode.contains(Loadable.Mode.THREAD)) continue;
            String name = StringUtils.caseAndSpace(postsOrder.name(), "_", true);
            if (postsOrder == ChanSettings.threadOrder.get()) {
                name = "\u2713 " + name; // Checkmark
            }

            items.add(new FloatingMenuItem<>(postsOrder, name));
        }
        ToolbarMenuItem overflow = navigation.findOverflow();
        View anchor = (item != null ? item : overflow).getView();
        FloatingMenu<PostsFilter.PostsOrder> menu;
        if (anchor != null) {
            menu = new FloatingMenu<>(context, anchor, items);
        } else {
            Logger.wtf(this, "Couldn't find anchor for sorting button action??");
            menu = new FloatingMenu<>(context, view, items);
        }

        menu.setCallback(new FloatingMenu.ClickCallback<PostsFilter.PostsOrder>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<PostsFilter.PostsOrder> menu, FloatingMenuItem<PostsFilter.PostsOrder> item
            ) {
                PostsFilter.PostsOrder postsOrder = item.getId();
                ChanSettings.threadOrder.set(postsOrder);
                presenter.setOrder(postsOrder);
            }
        });
        menu.show();
    }
}
