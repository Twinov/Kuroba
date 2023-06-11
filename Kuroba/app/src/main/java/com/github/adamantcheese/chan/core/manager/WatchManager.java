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
package com.github.adamantcheese.chan.core.manager;

import static com.github.adamantcheese.chan.Chan.ActivityForegroundStatus.IN_BACKGROUND;
import static com.github.adamantcheese.chan.core.manager.WatchManager.IntervalType.BACKGROUND;
import static com.github.adamantcheese.chan.core.manager.WatchManager.IntervalType.FOREGROUND;
import static com.github.adamantcheese.chan.core.manager.WatchManager.IntervalType.NONE;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.WatchNotifyMode.NOTIFY_ALL_POSTS;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.WatchNotifyMode.NOTIFY_ONLY_QUOTES;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getJobScheduler;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.isInForeground;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.*;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.features.notifications.LastPageNotification;
import com.github.adamantcheese.chan.features.notifications.WatchNotification;
import com.github.adamantcheese.chan.utils.*;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.*;

/**
 * Manages all Pin related management.
 * <p/>
 * <p>Pins are threads that are pinned to a pane on the left.
 * <p/>
 * <p>The pin watcher is an optional feature that watches threads for new posts and displays a new
 * post counter next to the pin view. Watching happens with the same backoff timer as used for
 * the auto updater for open threads.
 * <p/>
 * <p>Background watching is a feature that can be enabled. With background watching enabled then
 * the PinManager will register an AlarmManager to check for updates in intervals. It will acquire
 * a wakelock shortly while checking for updates.
 * <p/>
 * <p>All pin adding and removing must go through this class to properly update the watchers.
 */
public class WatchManager
        implements WakeManager.Wakeable {
    private static final Intent WATCH_NOTIFICATION_INTENT = new Intent(getAppContext(), WatchNotification.class);

    enum IntervalType {
        /**
         * A timer that uses a {@link Handler} that calls {@link #update(boolean)} every
         * {@link #FOREGROUND_INTERVAL}
         */
        FOREGROUND,

        /**
         * A timer that schedules a broadcast to be send that calls {@link #update(boolean)}.
         */
        BACKGROUND,

        /**
         * No scheduling.
         */
        NONE
    }

    private final Handler handler;
    private static final long FOREGROUND_INTERVAL = SECONDS.toMillis(15);
    private static final int MESSAGE_UPDATE = 1;

    private static final long STATE_UPDATE_DEBOUNCE_TIME_MS = 1000L;

    private final DatabasePinManager databasePinManager;

    private IntervalType currentInterval = NONE;
    private final List<Pin> pins;
    private final Debouncer stateUpdateDebouncer;

    private final Map<Pin, PinWatcher> pinWatchers = new HashMap<>();
    private Set<PinWatcher> waitingForPinWatchersForBackgroundUpdate;

    public WatchManager(
            DatabasePinManager databasePinManager
    ) {
        stateUpdateDebouncer = new Debouncer(true);
        this.databasePinManager = databasePinManager;

        pins = Collections.synchronizedList(DatabaseUtils.runTask(databasePinManager.getPins()));
        Collections.sort(pins);

        //register this manager to watch for setting changes and post pin changes
        EventBus.getDefault().register(this);

        //setup handler to deal with foreground updates
        handler = new Handler(Looper.getMainLooper(), msg -> {
            if (msg.what == MESSAGE_UPDATE) {
                update(false);
                return true;
            } else {
                return false;
            }
        });

        updateState();
    }

    public void createPin(Loadable loadable) {
        createPin(loadable, true);
    }

    public boolean createPin(Loadable loadable, boolean sendBroadcast) {
        Pin pin = new Pin();
        pin.loadable = loadable;
        return createPin(pin, sendBroadcast);
    }

    public void createPin(Pin pin) {
        createPin(pin, true);
    }

    public boolean createPin(Pin pin, boolean sendBroadcast) {
        synchronized (pins) {
            // No duplicates
            for (Pin e : pins) {
                if (e.loadable.equals(pin.loadable)) {
                    return false;
                }
            }

            // Default order is 0.
            pin.order = pin.order < 0 ? 0 : pin.order;

            // Move all down one.
            for (Pin p : pins) {
                p.order++;
            }
            pins.add(pin);
            DatabaseUtils.runTask(databasePinManager.createPin(pin));

            // apply orders.
            Collections.sort(pins);
            reorder();
            updateState();

            if (sendBroadcast) {
                postToEventBus(new PinMessages.PinAddedMessage(pin));
            }

            return true;
        }
    }

    @Nullable
    public Pin getPinByLoadable(Loadable loadable) {
        for (Pin pin : pins) {
            if (pin.loadable.equals(loadable)) {
                return pin;
            }
        }

        return null;
    }

    public void deletePin(Pin pin) {
        synchronized (pins) {
            int index = pins.indexOf(pin);
            pins.remove(pin);

            destroyPinWatcher(pin);

            DatabaseUtils.runTask(databasePinManager.deletePin(pin));

            // Update the new orders
            Collections.sort(pins);
            reorder();
            updateState();

            postToEventBus(new PinMessages.PinRemovedMessage(index));
        }
    }

    public void deletePins(List<Pin> pinList) {
        synchronized (pins) {
            for (Pin pin : pinList) {
                pins.remove(pin);
                destroyPinWatcher(pin);
            }

            DatabaseUtils.runTask(databasePinManager.deletePins(pinList));

            // Update the new orders
            Collections.sort(pins);
            reorder();
            updatePinsInDatabase();

            updateState();
            postToEventBus(new PinMessages.PinsChangedMessage());
        }
    }

    public void updatePin(Pin pin) {
        updatePin(pin, true);
    }

    public void updatePin(Pin pin, boolean updateState) {
        updatePinsInternal(Collections.singletonList(pin));
        DatabaseUtils.runTask(databasePinManager.updatePin(pin));

        if (updateState) {
            updateState();
        }

        postToEventBus(new PinMessages.PinChangedMessage(pin));
    }

    public void updatePins(List<Pin> updatedPins, boolean updateState) {
        updatePinsInternal(updatedPins);
        updatePinsInDatabase();

        if (updateState) {
            updateState();
        }

        for (Pin updatedPin : updatedPins) {
            postToEventBus(new PinMessages.PinChangedMessage(updatedPin));
        }
    }

    private void updatePinsInternal(List<Pin> updatedPins) {
        synchronized (pins) {
            Set<Pin> foundPins = new HashSet<>();

            for (Pin updatedPin : updatedPins) {
                for (int i = 0; i < pins.size(); i++) {
                    if (pins.get(i).loadable.id == updatedPin.loadable.id) {
                        pins.set(i, updatedPin);
                        foundPins.add(updatedPin);
                        break;
                    }
                }
            }

            for (Pin updatedPin : updatedPins) {
                if (!foundPins.contains(updatedPin)) {
                    pins.add(updatedPin);
                }
            }
        }
    }

    public Pin findPinByLoadableId(int loadableId) {
        synchronized (pins) {
            for (Pin pin : pins) {
                if (pin.loadable.id == loadableId) {
                    return pin;
                }
            }
        }

        return null;
    }

    public Pin findPinById(int id) {
        synchronized (pins) {
            for (Pin pin : pins) {
                if (pin.id == id) {
                    return pin;
                }
            }
        }

        return null;
    }

    public void reorder() {
        synchronized (pins) {
            for (int i = 0; i < pins.size(); i++) {
                pins.get(i).order = i;
            }
        }
        updatePinsInDatabase();
    }

    public List<Pin> getWatchingPins() {
        if (ChanSettings.watchEnabled.get()) {
            List<Pin> l = new ArrayList<>();

            synchronized (pins) {
                for (Pin p : pins) {
                    if (p.watching) {
                        l.add(p);
                    }
                }
            }

            return l;
        } else {
            return Collections.emptyList();
        }
    }

    public void toggleWatch(Pin pin) {
        if (pin.archived || pin.isError) {
            return;
        }

        pin.watching = !pin.watching;

        updateState();
        postToEventBus(new PinMessages.PinChangedMessage(pin));
    }

    public void onBottomPostViewed(Pin pin) {
        if (pin.watchNewCount >= 0) {
            pin.watchLastCount = pin.watchNewCount;
        }

        if (pin.quoteNewCount >= 0) {
            pin.quoteLastCount = pin.quoteNewCount;
        }

        PinWatcher pinWatcher = getPinWatcher(pin);
        if (pinWatcher != null) {
            //onViewed
            pinWatcher.wereNewPosts = false;
            pinWatcher.wereNewQuotes = false;
        }

        updatePin(pin);
    }

    @Subscribe
    public void onEvent(Chan.ActivityForegroundStatus status) {
        updateState();
        if (status == IN_BACKGROUND) {
            updatePinsInDatabase();
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> settingChanged) {
        if (settingChanged.setting == ChanSettings.watchBackground
                || settingChanged.setting == ChanSettings.watchEnabled) {
            updateState();
            postToEventBus(new PinMessages.PinsChangedMessage());
        }
    }

    // Called when the broadcast scheduled by the alarm manager was received
    public void onWake() {
        update(true);
    }

    // Called from the button on the notification
    public void pauseAll() {
        for (Pin pin : getWatchingPins()) {
            pin.watching = false;
        }

        updateState();
        updatePinsInDatabase();

        postToEventBus(new PinMessages.PinsChangedMessage());
    }

    // Clear all non watching pins or all pins
    // Returns a list of pins that can later be given to addAll to undo the clearing
    public List<Pin> clearPins(boolean all) {
        synchronized (pins) {
            List<Pin> toRemove = new ArrayList<>();
            if (all) {
                toRemove.addAll(pins);
            } else {
                for (Pin pin : pins) {
                    //if we're watching and a pin isn't being watched and has no flags, it's a candidate for clearing
                    //if the pin is archived or errored out, it's a candidate for clearing
                    if ((ChanSettings.watchEnabled.get() && !pin.watching) || pin.archived || pin.isError) {
                        toRemove.add(pin);
                    }
                }
            }

            List<Pin> undo = new ArrayList<>(toRemove.size());
            for (Pin pin : toRemove) {
                undo.add(pin.clone());
            }
            deletePins(toRemove);
            return undo;
        }
    }

    public List<Pin> getAllPins() {
        return pins; // TODO see if synchronization is needed here
    }

    public void addAll(List<Pin> pins) {
        Collections.sort(pins);
        for (Pin pin : pins) {
            createPin(pin);
        }
    }

    @Nullable
    public PinWatcher getPinWatcher(Pin pin) {
        return pinWatchers.get(pin);
    }

    private void createPinWatcher(Pin pin) {
        if (!pinWatchers.containsKey(pin)) {
            pinWatchers.put(pin, new PinWatcher(pin));
            postToEventBus(new PinMessages.PinChangedMessage(pin));
        }
    }

    private void destroyPinWatcher(Pin pin) {
        PinWatcher pinWatcher = pinWatchers.remove(pin);
        if (pinWatcher != null) {
            pinWatcher.destroy();
        }
    }

    private void updatePinsInDatabase() {
        List<Pin> clonedPins = new ArrayList<>();
        synchronized (pins) {
            for (Pin pin : pins) {
                clonedPins.add(pin.clone());
            }
        }
        DatabaseUtils.runTaskAsync(databasePinManager.updatePins(clonedPins));
    }

    // Update the interval type according to the current settings,
    // create and destroy PinWatchers where needed and update the notification
    private void updateState() {
        BackgroundUtils.ensureMainThread();

        // updateState() (which is now called updateStateInternal) was called way too often. It was
        // called once per every active pin. Because of that startService/stopService was called way
        // too often too, which also led to notification being updated too often, etc.
        // All of that could sometimes cause the notification to turn into a silent notification.
        // So to avoid this and to reduce the amount of pin updates per second a debouncer was
        // introduced. It updateState() is called too often, it will skip all updates and will wait
        // for at least STATE_UPDATE_DEBOUNCE_TIME_MS without any updates before calling
        // updateStateInternal().
        stateUpdateDebouncer.post(this::updateStateInternal, STATE_UPDATE_DEBOUNCE_TIME_MS);
    }

    private void updateStateInternal() {
        BackgroundUtils.ensureMainThread();

        updateIntervals();

        // Update notification state
        // Do not start the service when all pins are stopped or archived/404ed
        if (updatePinWatchers() && !getWatchingPins().isEmpty() && ChanSettings.watchBackground.get()) {
            // To make sure that we won't blow up when starting a service while the app is in
            // background we have to use this method which will call context.startForegroundService()
            // that allows an app to start a service (which must then call StartForeground in it's
            // onCreate method) while being in background.
            ContextCompat.startForegroundService(getAppContext(), WATCH_NOTIFICATION_INTENT);
        } else {
            getAppContext().stopService(WATCH_NOTIFICATION_INTENT);
        }
    }

    /**
     * @return true if there are unread posts in active threads
     */
    private boolean updatePinWatchers() {
        boolean hasActiveUnreadPins = false;
        synchronized (pins) {
            List<Pin> pinsToUpdateInDatabase = new ArrayList<>();

            for (Pin pin : pins) {
                if (pin.isError || pin.archived) {
                    pin.watching = false;
                }

                if (pin.isError) {
                    // When a thread gets deleted just mark all posts as read
                    // since there is no way for us to read them anyway
                    pin.watchLastCount = pin.watchNewCount;
                    pinsToUpdateInDatabase.add(pin);
                }

                if (ChanSettings.watchEnabled.get()) {
                    createPinWatcher(pin);
                } else {
                    destroyPinWatcher(pin);
                }

                if (pin.watching) {
                    if (ChanSettings.watchNotifyMode.get() == NOTIFY_ALL_POSTS) {
                        if (pin.watchLastCount != pin.watchNewCount) {
                            hasActiveUnreadPins = true;
                        }
                    } else if (ChanSettings.watchNotifyMode.get() == NOTIFY_ONLY_QUOTES) {
                        if (pin.quoteLastCount != pin.quoteNewCount) {
                            hasActiveUnreadPins = true;
                        }
                    }
                }
            }

            if (pinsToUpdateInDatabase.size() > 0) {
                updatePins(pinsToUpdateInDatabase, false);
            }
        }

        return hasActiveUnreadPins;
    }

    private void updateIntervals() {
        IntervalType newInterval = NONE;
        if (ChanSettings.watchEnabled.get()) {
            if (isInForeground()) {
                newInterval = FOREGROUND;
            } else {
                if (ChanSettings.watchBackground.get()) {
                    newInterval = BACKGROUND;
                }
            }
        }

        if (hasActivePins()) {
            if (currentInterval == newInterval) return;
            endIntervalActions();

            Logger.vd(this, "Setting interval type from " + currentInterval.name() + " to " + newInterval.name());
            currentInterval = newInterval;

            beginIntervalActions();
        } else {
            Logger.vd(this, "No active pins found, unregistering for wake");
            endIntervalActions();
        }
    }

    private void endIntervalActions() {
        switch (currentInterval) {
            case FOREGROUND:
                // Stop receiving handler messages
                handler.removeMessages(MESSAGE_UPDATE);
                break;
            case BACKGROUND:
                // Stop receiving scheduled broadcasts
                WakeManager.getInstance().unregisterWakeable(this);
                break;
            case NONE:
                // Stop everything
                handler.removeMessages(MESSAGE_UPDATE);
                WakeManager.getInstance().unregisterWakeable(this);
                break;
        }
    }

    private void beginIntervalActions() {
        switch (currentInterval) {
            case FOREGROUND:
                //Background/none -> foreground means start receiving foreground updates
                handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_UPDATE), FOREGROUND_INTERVAL);
                break;
            case BACKGROUND:
                //Foreground/none -> background means start receiving background updates
                WakeManager.getInstance().registerWakeable(this);
                break;
            case NONE:
                //Foreground/background -> none means stop receiving every update
                handler.removeMessages(MESSAGE_UPDATE);
                WakeManager.getInstance().unregisterWakeable(this);
                break;
        }
    }

    private boolean hasActivePins() {
        for (Pin pin : pins) {
            if (!pin.isError && !pin.archived) {
                return true;
            }
        }

        return false;
    }

    // Update the watching pins
    private void update(boolean fromBackground) {
        Logger.vd(this, "update from " + (fromBackground ? "background" : "foreground"));

        if (currentInterval == FOREGROUND) {
            // reschedule handler message
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_UPDATE), FOREGROUND_INTERVAL);
        }

        // A set of watchers that all have to complete being updated
        // before the wakelock is released again
        waitingForPinWatchersForBackgroundUpdate = null;
        if (fromBackground) {
            waitingForPinWatchersForBackgroundUpdate = new HashSet<>();
        }

        List<Pin> watchingPins = getWatchingPins();
        for (Pin pin : watchingPins) {
            PinWatcher pinWatcher = getPinWatcher(pin);
            if (pinWatcher != null && pinWatcher.update(fromBackground)) {
                if (fromBackground) {
                    waitingForPinWatchersForBackgroundUpdate.add(pinWatcher);
                }
            }
        }

        if (fromBackground && !waitingForPinWatchersForBackgroundUpdate.isEmpty()) {
            Logger.i(
                    this,
                    waitingForPinWatchersForBackgroundUpdate.size()
                            + " pin watchers beginning updates, started at "
                            + StringUtils.getCurrentTimeDefaultLocale()
            );
            WakeManager.getInstance().manageLock(true, WatchManager.this);
        }
    }

    private void pinWatcherUpdated(PinWatcher pinWatcher) {
        updateState();
        postToEventBus(new PinMessages.PinChangedMessage(pinWatcher.pin));

        synchronized (WatchManager.this) {
            if (waitingForPinWatchersForBackgroundUpdate != null) {
                waitingForPinWatchersForBackgroundUpdate.remove(pinWatcher);

                if (waitingForPinWatchersForBackgroundUpdate.isEmpty()) {
                    Logger.i(this, "All watchers updated, finished at " + StringUtils.getCurrentTimeDefaultLocale());
                    waitingForPinWatchersForBackgroundUpdate = null;
                    WakeManager.getInstance().manageLock(false, WatchManager.this);
                }
            }
        }
    }

    public static class PinMessages {
        public static class PinAddedMessage {
            public Pin pin;

            public PinAddedMessage(Pin pin) {
                this.pin = pin;
            }
        }

        public static class PinRemovedMessage {
            public int index;

            public PinRemovedMessage(int index) {
                this.index = index;
            }
        }

        public static class PinChangedMessage {
            public Pin pin;

            public PinChangedMessage(Pin pin) {
                this.pin = pin;
            }
        }

        public static class PinsChangedMessage {
            public PinsChangedMessage() {}
        }
    }

    public class PinWatcher
            implements NetUtilsClasses.ResponseResult<ChanThread>, PageRepository.PageCallback {
        private final Pin pin;
        private ChanThreadLoader chanLoader;

        private final List<Post> posts = new ArrayList<>();
        private final List<Post> quotes = new ArrayList<>();
        private boolean wereNewQuotes = false;
        private boolean wereNewPosts = false;
        private boolean notified = true;

        public PinWatcher(Pin pin) {
            this.pin = pin;

            Logger.d(this, "created for " + pin.loadable.toString());
            chanLoader = ChanLoaderManager.obtain(pin.loadable, this);
            PageRepository.addListener(this);
        }

        public CharSequence getSummary() {
            return chanLoader != null && chanLoader.getThread() != null ? chanLoader.getThread().summarize(true) : null;
        }

        public List<Post> getUnviewedPosts() {
            if (posts.isEmpty()) {
                return posts;
            } else {
                return posts.subList(Math.max(0, posts.size() - pin.getNewPostCount()), posts.size());
            }
        }

        public List<Post> getUnviewedQuotes() {
            return quotes.subList(Math.max(0, quotes.size() - pin.getNewQuoteCount()), quotes.size());
        }

        public boolean getWereNewQuotes() {
            if (wereNewQuotes) {
                wereNewQuotes = false;
                return true;
            } else {
                return false;
            }
        }

        public boolean getWereNewPosts() {
            if (wereNewPosts) {
                wereNewPosts = false;
                return true;
            } else {
                return false;
            }
        }

        private void destroy() {
            if (chanLoader != null) {
                Logger.d(
                        this,
                        "PinWatcher: destroyed for pin with id " + pin.id + " and loadable" + pin.loadable.toString()
                );
                ChanLoaderManager.release(chanLoader, this);
                chanLoader = null;
            }
            PageRepository.removeListener(this);
        }

        /**
         * @param fromBackground was this update called from a background state
         * @return true if a data call was requested
         */
        private boolean update(boolean fromBackground) {
            if (!pin.isError && pin.watching) {
                //check last page stuff, get the page for the OP and notify in the onPages method
                doPageNotification();
                if (fromBackground) {
                    // Always load regardless of timer, since the time left is not accurate for 15min+ intervals
                    chanLoader.clearTimer();
                    chanLoader.requestAdditionalData();
                    return true;
                } else {
                    if (chanLoader.getTimeUntilLoadMore() < 0L) {
                        chanLoader.requestAdditionalData();
                        return true;
                    }
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public void onFailure(Exception error) {
            Logger.w(this, "onChanLoaderError()");

            // Ignore normal network errors, we only pause pins when there is absolutely no way
            // we'll ever need watching again: a 404.
            if (error instanceof NetUtilsClasses.HttpCodeException
                    && ((NetUtilsClasses.HttpCodeException) error).isServerErrorNotFound()) {
                pin.isError = true;
                pin.watching = false;
            }

            pinWatcherUpdated(this);
        }

        @Override
        public void onSuccess(ChanThread thread) {
            if (thread.getOp() != null) {
                pin.isSticky = thread.getOp().sticky;
            } else {
                pin.isSticky = false;
            }

            pin.isError = false;

            // Populate posts list
            posts.clear();
            posts.addAll(thread.getPosts());

            // Populate quotes list
            quotes.clear();

            // Get list of saved replies from this thread
            List<Post> savedReplies = new ArrayList<>();
            for (Post item : thread.getPosts()) {
                if (item.isSavedReply) {
                    savedReplies.add(item);
                }
            }

            // Now get a list of posts that have a quote to a saved reply, but not self-replies
            for (Post post : thread.getPosts()) {
                for (Post saved : savedReplies) {
                    if (post.repliesTo.contains(saved.no) && !post.isSavedReply) {
                        quotes.add(post);
                    }
                }
            }

            boolean isFirstLoad = pin.watchNewCount < 0 || pin.quoteNewCount < 0;

            // If it was more than before processing
            int lastWatchNewCount = pin.watchNewCount;
            int lastQuoteNewCount = pin.quoteNewCount;

            if (isFirstLoad) {
                pin.watchLastCount = posts.size();
                pin.quoteLastCount = quotes.size();
            }

            pin.watchNewCount = posts.size() - savedReplies.size();
            pin.quoteNewCount = quotes.size();

            if (!isFirstLoad) {
                // There were new posts after processing
                if (pin.watchNewCount > lastWatchNewCount) {
                    wereNewPosts = true;
                }

                // There were new quotes after processing
                if (pin.quoteNewCount > lastQuoteNewCount) {
                    wereNewQuotes = true;
                }
            }

            Logger.vd(this, String.format(
                    Locale.ENGLISH,
                    "postlast=%d postnew=%d werenewposts=%b quotelast=%d quotenew=%d werenewquotes=%b nextload=%ds",
                    pin.watchLastCount,
                    pin.watchNewCount,
                    wereNewPosts,
                    pin.quoteLastCount,
                    pin.quoteNewCount,
                    wereNewQuotes,
                    chanLoader.getTimeUntilLoadMore() / 1000
            ));

            if (thread.isArchived() || thread.isClosed()) {
                pin.archived = true;
                pin.watching = false;
            }

            pinWatcherUpdated(this);
        }

        @Override
        public void onPagesReceived() {
            doPageNotification();
        }

        private void doPageNotification() {
            ChanPage page = PageRepository.getPage(chanLoader.getLoadable());
            if (ChanSettings.watchEnabled.get()
                    && ChanSettings.watchLastPageNotify.get()
                    && ChanSettings.watchBackground.get()) {
                if (page != null && page.page >= pin.loadable.board.pages && !notified) {
                    lastPageNotify(true); //schedules a job to notify the user of a last page
                    notified = true;
                } else if (page != null && page.page < pin.loadable.board.pages) {
                    lastPageNotify(false); //schedules a job to cancel the notification; don't use cancel!
                    notified = false;
                }
            }
        }

        private void lastPageNotify(boolean notify) {
            ComponentName lastPageNotifyClass = new ComponentName(getAppContext(), LastPageNotification.class);
            JobInfo.Builder builder = new JobInfo.Builder(pin.loadable.no, lastPageNotifyClass);
            builder.setOverrideDeadline(MINUTES.toMillis(1));
            PersistableBundle extras = new PersistableBundle();
            extras.putInt(LastPageNotification.PIN_ID_KEY, pin.id);
            extras.putInt(LastPageNotification.NOTIFY_KEY, notify ? 1 : 0);
            builder.setExtras(extras);
            getJobScheduler().schedule(builder.build());
        }
    }
}
