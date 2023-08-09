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
package com.github.adamantcheese.chan.core.site.parser;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.site.SiteEndpoints.IconType.OTHER;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.helper.ThemeHelper;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.PostLinkable;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.ThreadLinkable;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.*;
import java.util.concurrent.*;

import javax.inject.Inject;

import okhttp3.HttpUrl;

/**
 * Process a typical imageboard json response.<br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
public class ChanReaderParser
        implements NetUtilsClasses.Converter<ChanLoaderResponse, JsonReader> {

    @Inject
    FilterEngine filterEngine;

    @Inject
    DatabaseSavedReplyManager databaseSavedReplyManager;

    @Inject
    DatabaseHideManager databaseHideManager;

    private final Loadable loadable;
    private final List<Post> cached;
    private final SiteContentReader reader;

    /**
     * @param loadable    The loadable associated with this parser
     * @param cachedPosts A list of cached posts; may be an empty list for no cached post processing
     * @param reader      A reader to process posts for a request; if null, the reader associated with the loadable's site will be used
     */
    public ChanReaderParser(Loadable loadable, @NonNull List<Post> cachedPosts, @Nullable SiteContentReader reader) {
        inject(this);

        // Copy the cached list. The cached array may changed/cleared by other threads.
        this.loadable = loadable;
        cached = new ArrayList<>(cachedPosts);
        this.reader = reader == null ? this.loadable.site.chanReader() : reader;
    }

    @Override
    public ChanLoaderResponse convert(JsonReader reader)
            throws Exception {
        ChanReaderProcessingQueue processing = new ChanReaderProcessingQueue(cached, loadable);

        if (loadable.isThreadMode()) {
            this.reader.loadThread(reader, processing);
        } else if (loadable.isCatalogMode()) {
            this.reader.loadCatalog(reader, processing);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        List<PostHide> removedPosts;
        try {
            removedPosts = databaseHideManager.getRemovedPostsWithThreadNo(processing.getOp().no);
        } catch (Exception e) {
            removedPosts = Collections.emptyList();
        }

        // add in extra removed posts from filters (for cached posts)
        for (Post post : processing.getToReuse()) {
            if (post.filterRemove) {
                removedPosts.add(new PostHide(post.board.siteId, post.boardCode, post.no));
            }
        }

        List<Post> list = parsePosts(processing, removedPosts);
        return processPosts(processing.getOp(), list, removedPosts);
    }

    // Concurrently parses the new posts with an executor
    private List<Post> parsePosts(ChanReaderProcessingQueue queue, List<PostHide> removedPosts)
            throws InterruptedException, ExecutionException {
        List<Post> cached = queue.getToReuse();
        List<Post> total = new ArrayList<>(cached);

        List<Post.Builder> toParse = queue.getToParse();

        // A set of all post numbers in the thread. Used for checking if a quote if for the current thread or externally.
        Set<Integer> internalNums = new HashSet<>();
        // All nos of cached posts.
        for (Post post : cached) {
            internalNums.add(post.no);
        }
        // And nos for posts to parse, from the builder.
        for (Post.Builder builder : toParse) {
            internalNums.add(builder.no);
        }
        // Do not modify internalNums after this point.
        internalNums = Collections.unmodifiableSet(internalNums);

        List<Callable<Post>> tasks = new ArrayList<>(toParse.size());
        final Theme currentTheme = ThemeHelper.getTheme();

        for (Post.Builder post : toParse) {
            tasks.add(new PostParseCallable(databaseSavedReplyManager,
                    post,
                    reader.getParser(),
                    removedPosts,
                    internalNums,
                    currentTheme
            ));
        }

        List<Future<Post>> futures = BackgroundUtils.backgroundService.invokeAll(tasks);
        for (Future<Post> f : futures) {
            Post p = f.get();
            if (p != null) {
                total.add(p);
            }
        }
        return total;
    }

    private ChanLoaderResponse processPosts(Post.Builder op, List<Post> allPost, List<PostHide> removedPosts) {
        ChanLoaderResponse response = new ChanLoaderResponse(op);

        List<Post> cachedPosts = new ArrayList<>();
        List<Post> newPosts = new ArrayList<>();
        List<Post> deletedPosts = new ArrayList<>();

        if (cached.size() > 0) {
            // Add all posts that were parsed before
            cachedPosts.addAll(cached);

            Map<Integer, Post> cachedPostsByNo = new HashMap<>();
            for (Post post : cachedPosts) {
                cachedPostsByNo.put(post.no, post);
            }

            Map<Integer, Post> serverPostsByNo = new HashMap<>();
            for (Post post : allPost) {
                serverPostsByNo.put(post.no, post);
            }

            // If there's a cached post but it's not in the list received from the server, mark it as deleted
            if (loadable.isThreadMode()) {
                for (Post cachedPost : cachedPosts) {
                    cachedPost.deleted = !serverPostsByNo.containsKey(cachedPost.no);
                    if (cachedPost.deleted) {
                        deletedPosts.add(cachedPost);
                    }
                }
            }

            // If there's a post in the list from the server, that's not in the cached list, add it.
            for (Post serverPost : allPost) {
                if (!cachedPostsByNo.containsKey(serverPost.no)) {
                    newPosts.add(serverPost);
                }
            }
        } else {
            newPosts.addAll(allPost);
        }

        List<Post> allPosts = new ArrayList<>(cachedPosts.size() + newPosts.size());
        allPosts.addAll(cachedPosts);
        allPosts.addAll(newPosts);

        if (ChanSettings.markNewIps.get()) {
            int oldUniqueIps = allPosts.get(0).uniqueIps;
            int newUniqueIps = op.uniqueIps;
            int oldPostCount = cachedPosts.size();
            int newPostCount = allPosts.size();
            int deletedPostCount = deletedPosts.size();

            if (newUniqueIps - oldUniqueIps == newPostCount - oldPostCount + deletedPostCount) {
                int ipNo = oldUniqueIps;
                for (Post post : newPosts) {
                    int ipNumInThread = ++ipNo; // new IP, this is the number in the thread
                    post.httpIcons.add(new PostHttpIcon(OTHER, null, new PassthroughBitmapResult() {
                        @Override
                        public void onBitmapSuccess(
                                @NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache
                        ) {
                            super.onBitmapSuccess(source, BitmapRepository.newIpIcon, fromCache);
                        }
                    }, "IP #", "" + ipNumInThread));
                }
            }
        }

        for (Post post : newPosts) {
            // add in removed posts from new posts
            if (post.filterRemove) {
                removedPosts.add(new PostHide(post.board.siteId, post.boardCode, post.no));
            }

            // for any new posts in a thread, toss them through the filter watcher to attempt to follow
            if (cachedPosts.isEmpty())
                continue; // wait until threads get loaded initially, to not process all posts at once
            for (PostLinkable<?> linkable : post.getLinkables()) {
                if (linkable instanceof ThreadLinkable) {
                    ThreadLink link = (ThreadLink) linkable.value;
                    Loadable thread =
                            Loadable.forThread(post.board, link.threadId, PostHelper.getTitle(post, null), false);
                    BackgroundUtils.runOnMainThread(() -> instance(FilterWatchManager.class).checkExternalThread(thread));
                }
            }
        }

        if (loadable.isThreadMode()) {
            Map<Integer, Post> postsByNo = new HashMap<>();
            for (Post post : allPosts) {
                postsByNo.put(post.no, post);
            }

            // Maps post no's to a list of no's that that post received replies from
            Map<Integer, List<Integer>> replies = new HashMap<>();

            // for all posts, for any posts this post is replying to (ie has >>1234), add this post to a list of numbers for the replying number
            // ie map this post to another post's repliesFrom, temporarily
            for (Post sourcePost : allPosts) {
                for (int replyTo : sourcePost.repliesTo) {
                    List<Integer> value = replies.get(replyTo);
                    if (value == null) {
                        value = new ArrayList<>(1);
                        replies.put(replyTo, value);
                    }
                    value.add(sourcePost.no);
                }
            }

            // for all post numbers, now properly assign the repliesFrom field, removing any removed posts along the way
            for (Map.Entry<Integer, List<Integer>> entry : replies.entrySet()) {
                int key = entry.getKey();
                List<Integer> value = entry.getValue();
                Post subject = postsByNo.get(key);

                // Sometimes a post replies to a ghost, a post that doesn't exist.
                if (subject != null) {
                    // If a post has been removed, remove it from the replies list
                    Iterator<Integer> repliesFrom = value.iterator();
                    while (repliesFrom.hasNext()) {
                        Integer replyFrom = repliesFrom.next();
                        if (removedPosts.contains(new PostHide(subject.board.siteId, subject.board.code, replyFrom))) {
                            repliesFrom.remove();
                        }
                    }

                    subject.repliesFrom.clear();
                    subject.repliesFrom.addAll(value);
                }
            }
        }

        response.posts.addAll(allPosts);

        return response;
    }
}
