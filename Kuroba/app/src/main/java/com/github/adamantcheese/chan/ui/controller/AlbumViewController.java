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

import static com.github.adamantcheese.chan.core.saver.ImageSaveTask.copyImageToClipboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;

import android.content.Context;
import android.view.*;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.AlbumLayout;
import com.github.adamantcheese.chan.ui.view.ShapeablePostImageView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;

import java.util.List;

public class AlbumViewController
        extends Controller
        implements ImageViewerController.ImageViewerCallback, ImageViewerController.GoPostCallback,
                   ToolbarNavigationController.ToolbarSearchCallback {
    private enum MenuId {
        DOWNLOAD_ALBUM
    }

    private AlbumLayout albumlayout;

    private List<PostImage> postImages;
    private PostImage targetStartingImage;

    private Loadable loadable;
    private final ProxyImageViewerCallback callback;

    public AlbumViewController(Context context, ProxyImageViewerCallback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // View setup
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_album_view, null);
        albumlayout = view.findViewById(R.id.recycler_view);
        albumlayout.setAdapter(new AlbumAdapter());
        albumlayout.scrollToPositionWithOffset(postImages.indexOf(targetStartingImage), 0);
        targetStartingImage = null;
    }

    public void setImages(Loadable loadable, List<PostImage> postImages, PostImage start, String title) {
        this.loadable = loadable;
        this.postImages = postImages;

        navigation
                .buildMenu()
                .withItem(MenuId.DOWNLOAD_ALBUM,
                        R.drawable.ic_fluent_table_move_below_24_filled,
                        this::downloadAlbumClicked
                )
                .build();

        navigation.title = title;
        navigation.subtitle = getQuantityString(R.plurals.image, postImages.size());
        targetStartingImage = start;
    }

    private void downloadAlbumClicked(@SuppressWarnings("unused") ToolbarMenuItem item) {
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(loadable, postImages);
        navigationController.pushController(albumDownloadController);
    }

    @Override
    public ImageView getPreviewImageTransitionView(PostImage postImage) {
        RecyclerView.ViewHolder holder = albumlayout.findViewHolderForAdapterPosition(postImages.indexOf(postImage));
        if (holder instanceof AlbumAdapter.AlbumItemCellHolder) {
            AlbumAdapter.AlbumItemCellHolder cellHolder = (AlbumAdapter.AlbumItemCellHolder) holder;
            return cellHolder.thumbnailView;
        }
        return null;
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        albumlayout.smoothScrollToPosition(postImages.indexOf(postImage));
    }

    @Override
    public ImageViewerController.ImageViewerCallback goToPost(PostImage postImage) {
        ThreadController threadController = null;

        if (previousSiblingController instanceof DoubleNavigationController) {
            //slide or phone mode
            DoubleNavigationController doubleNav = (DoubleNavigationController) previousSiblingController;
            if (doubleNav.getRightController() instanceof ThreadController) {
                threadController = (ThreadController) doubleNav.getRightController();
            }
        } else if (previousSiblingController == null) {
            //split nav has no "sibling" to look at, so we go WAY back to find the view thread controller
            SplitNavigationController splitNav =
                    (SplitNavigationController) this.parentController.parentController.presentedByController;
            threadController = (ThreadController) splitNav.rightController.childControllers.get(0);
            threadController.selectPostImage(postImage);
            //clear the popup here because split nav is weirdly laid out in the stack
            splitNav.popController();
            return threadController;
        }

        if (threadController != null) {
            threadController.selectPostImage(postImage);
            navigationController.popController(false);
            return threadController;
        } else {
            return null;
        }
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return callback.getPostForPostImage(postImage);
    }

    @Override
    public void onNavItemSet() {
        AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("DownloadAlbumHint")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.album_download_hint)
                .build()
                .showAlignBottom(navigation.findItem(MenuId.DOWNLOAD_ALBUM).getView());
    }

    private class AlbumAdapter
            extends RecyclerView.Adapter<AlbumAdapter.AlbumItemCellHolder> {

        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public AlbumItemCellHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AlbumItemCellHolder(LayoutInflater
                    .from(parent.getContext())
                    .inflate(ChanSettings.useStaggeredAlbumGrid.get()
                            ? R.layout.cell_album_view_stagger
                            : R.layout.cell_album_view, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AlbumItemCellHolder holder, int position) {
            holder.postImage = postImages.get(position);
            holder.thumbnailView.setType(holder.postImage);
            holder.loadPostImage(holder.postImage, holder.thumbnailView);
        }

        @Override
        public void onViewRecycled(@NonNull AlbumItemCellHolder holder) {
            holder.postImage = null;
            holder.thumbnailView.setType(null);
            holder.cancelLoad(holder.thumbnailView);
        }

        @Override
        public int getItemCount() {
            return postImages.size();
        }

        @Override
        public long getItemId(int position) {
            return postImages.get(position).imageUrl.hashCode();
        }

        private class AlbumItemCellHolder
                extends RecyclerView.ViewHolder
                implements ImageLoadable {
            private PostImage postImage;
            private final ShapeablePostImageView thumbnailView;
            private ImageLoadableData data;

            public AlbumItemCellHolder(View view) {
                super(view);
                this.thumbnailView = view.findViewById(R.id.image);

                if (ChanSettings.enableLongPressURLCopy.get()) {
                    thumbnailView.setOnLongClickListener(v -> copyImageToClipboard(v.getContext(), postImage));
                }

                thumbnailView.setOnClickListener(v -> {
                    final ImageViewerNavigationController imageViewer = new ImageViewerNavigationController(context);
                    int index = postImages.indexOf(postImage);
                    presentController(imageViewer, false);
                    imageViewer.showImages(postImages,
                            index,
                            loadable,
                            AlbumViewController.this,
                            AlbumViewController.this
                    );
                });
            }

            @Override
            public ImageLoadableData getImageLoadableData() {
                return data;
            }

            @Override
            public void setImageLoadableData(ImageLoadableData data) {
                this.data = data;
            }
        }
    }

    public interface ProxyImageViewerCallback {
        Post getPostForPostImage(PostImage postImage);
    }
}
