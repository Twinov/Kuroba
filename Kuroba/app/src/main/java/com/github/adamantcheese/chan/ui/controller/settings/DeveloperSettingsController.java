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

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.*;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.WebviewSyncCookieManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.controller.LogsController;
import com.github.adamantcheese.chan.utils.Logger;
import com.skydoves.balloon.BalloonPersistence;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import javax.inject.Inject;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class DeveloperSettingsController
        extends Controller {
    @Inject
    FilterWatchManager filterWatchManager;
    @Inject
    DatabaseHelper databaseHelper;
    @Inject
    WakeManager wakeManager;

    public DeveloperSettingsController(Context context) {
        super(context);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_developer);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        //VIEW LOGS
        Button logsButton = new Button(context);
        logsButton.setOnClickListener(v -> navigationController.pushController(new LogsController(context)));
        logsButton.setText(R.string.settings_open_logs);
        wrapper.addView(logsButton);

        // Enable/Disable verbose logs
        Switch verboseLogsSwitch = new Switch(context);
        verboseLogsSwitch.setText("Verbose logging");
        verboseLogsSwitch.setTextColor(getAttrColor(context, android.R.attr.textColor));
        verboseLogsSwitch.setChecked(ChanSettings.verboseLogs.get());
        verboseLogsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> ChanSettings.verboseLogs.toggle());
        wrapper.addView(verboseLogsSwitch);

        //CRASH APP
        Button crashButton = new Button(context);
        crashButton.setOnClickListener(v -> {
            throw new RuntimeException("Debug crash");
        });
        crashButton.setText("Crash the app");
        wrapper.addView(crashButton);

        //DATABASE SUMMARY
        TextView summaryText = new TextView(context);
        summaryText.setText("Database summary:\n" + DatabaseUtils.getDatabaseSummary());
        updatePaddings(summaryText, 0, 0, dp(context, 5), 0);
        wrapper.addView(summaryText);

        //APP RESET
        Button resetDbButton = new Button(context);
        resetDbButton.setOnClickListener(v -> {
            databaseHelper.reset();
            for (Field f : ChanSettings.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && Setting.class.isAssignableFrom(f.getType())) {
                    try {
                        Setting setting = (Setting) f.get(ChanSettings.class);
                        setting.setSync(setting.getDefault());
                    } catch (Exception e) {
                        Logger.e(this, "", e);
                    }
                }
            }
            for (Field f : PersistableChanState.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && Setting.class.isAssignableFrom(f.getType())) {
                    try {
                        Setting setting = (Setting) f.get(ChanSettings.class);
                        setting.setSync(setting.getDefault());
                    } catch (Exception e) {
                        Logger.e(this, "", e);
                    }
                }
            }
            BalloonPersistence.getInstance(context).clearAllPreferences();
            ((WebviewSyncCookieManager) NetUtils.applicationClient.cookieJar()).clearAllCookies();
            ((StartActivity) context).restartApp();
        });
        resetDbButton.setText("Reset application and restart fresh");
        wrapper.addView(resetDbButton);

        //FILTER WATCH IGNORE RESET
        Button clearFilterWatchIgnores = new Button(context);
        clearFilterWatchIgnores.setOnClickListener(v -> {
            try {
                Field ignoredField = filterWatchManager.getClass().getDeclaredField("ignoredPosts");
                ignoredField.setAccessible(true);
                ignoredField.set(filterWatchManager, Collections.synchronizedSet(new HashSet<Integer>()));
                showToast(context, "Cleared ignores");
            } catch (Exception e) {
                showToast(context, "Failed to clear ignores");
            }
        });
        clearFilterWatchIgnores.setText("Clear ignored filter watches");
        wrapper.addView(clearFilterWatchIgnores);

        Button clearVideoTitleCache = new Button(context);
        clearVideoTitleCache.setOnClickListener(v -> {
            EmbeddingEngine.getInstance().clearCache();
            showToast(context, "Cleared video title cache");
        });
        clearVideoTitleCache.setText("Clear video title cache");
        wrapper.addView(clearVideoTitleCache);

        //THREAD STACK DUMPER
        Button dumpAllThreadStacks = new Button(context);
        dumpAllThreadStacks.setOnClickListener(v -> {
            Set<Thread> activeThreads = Thread.getAllStackTraces().keySet();
            Logger.i(this, "Thread count: " + activeThreads.size());
            for (Thread t : activeThreads) {
                //ignore these threads as they aren't relevant (main will always be this button press)
                //@formatter:off
                if (t.getName().equalsIgnoreCase("main")
                        || t.getName().contains("Daemon")
                        || t.getName().equalsIgnoreCase("Signal Catcher")
                        || t.getName().contains("hwuiTask")
                        || t.getName().contains("Binder:")
                        || t.getName().equalsIgnoreCase("RenderThread")
                        || t.getName().contains("maginfier pixel")
                        || t.getName().contains("Jit thread")
                        || t.getName().equalsIgnoreCase("Profile Saver")
                        || t.getName().contains("Okio")
                        || t.getName().contains("AsyncTask"))
                    //@formatter:on
                    continue;
                StackTraceElement[] elements = t.getStackTrace();
                Logger.i(this, "Thread: " + t.getName());
                for (StackTraceElement e : elements) {
                    Logger.i(this, e.toString());
                }
                Logger.i(this, "----------------");
            }
        });
        dumpAllThreadStacks.setText("Dump active thread stack traces to log");
        wrapper.addView(dumpAllThreadStacks);

        Switch threadCrashSwitch = new Switch(context);
        threadCrashSwitch.setText("Crash on wrong thread");
        threadCrashSwitch.setTextColor(getAttrColor(context, android.R.attr.textColor));
        threadCrashSwitch.setChecked(ChanSettings.crashOnWrongThread.get());
        threadCrashSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> ChanSettings.crashOnWrongThread.toggle());
        wrapper.addView(threadCrashSwitch);

        Switch noFunAllowed = new Switch(context);
        noFunAllowed.setText("No fun allowed?");
        noFunAllowed.setTextColor(getAttrColor(context, android.R.attr.textColor));
        noFunAllowed.setChecked(PersistableChanState.noFunAllowed.get());
        noFunAllowed.setOnCheckedChangeListener((buttonView, isChecked) -> PersistableChanState.noFunAllowed.toggle());
        wrapper.addView(noFunAllowed);

        TextView experimentalSection = new TextView(context);
        experimentalSection.setText("Experimental Settings");
        experimentalSection.setTextSize(16);
        experimentalSection.setGravity(Gravity.CENTER_HORIZONTAL);
        updatePaddings(experimentalSection, 0, 0, dp(context, 5), 0);
        wrapper.addView(experimentalSection);

        Switch roundedIdTest = new Switch(context);
        roundedIdTest.setText("Use rounded background span for IDs");
        roundedIdTest.setTextColor(getAttrColor(context, android.R.attr.textColor));
        roundedIdTest.setChecked(PersistableChanState.experimentalRoundedIDSpans.get());
        roundedIdTest.setOnCheckedChangeListener((buttonView, isChecked) -> PersistableChanState.experimentalRoundedIDSpans.toggle());
        wrapper.addView(roundedIdTest);

        Button testWebiew = new Button(context);
        testWebiew.setOnClickListener(v -> {
            HttpUrl example = HttpUrl.get("https://www.example.com");
            Cookie testCookie = new Cookie.Builder()
                    .domain("example.com")
                    .name("test")
                    .value("test2")
                    .expiresAt(Long.MAX_VALUE)
                    .build();

            int successes = 0;
            while (successes < 100) {
                try {
                    NetUtils.clearAllCookies(example);
                    List<Cookie> returned = NetUtils.applicationClient.cookieJar().loadForRequest(example);
                    assert returned.isEmpty();

                    NetUtils.applicationClient
                            .cookieJar()
                            .saveFromResponse(example, Collections.singletonList(testCookie));
                    returned = NetUtils.applicationClient.cookieJar().loadForRequest(example);
                    assert returned.size() == 1;
                    successes++;
                } catch (Throwable e) {
                    break;
                }
            }
            Logger.d(this, "Finished with successes: " + successes);
        });
        testWebiew.setText("Test webview cookie synchronization");
        wrapper.addView(testWebiew);

        Button clearBitmapCache = new Button(context);
        clearBitmapCache.setOnClickListener(v -> NetUtils.clearImageCache());
        clearBitmapCache.setText("Clear bitmap cache");
        wrapper.addView(clearBitmapCache);

        Button clearHintPersistence = new Button(context);
        clearHintPersistence.setOnClickListener(v -> BalloonPersistence.getInstance(context).clearAllPreferences());
        clearHintPersistence.setText("Clear hint persistence");
        wrapper.addView(clearHintPersistence);

        ScrollView scrollView = new ScrollView(context);
        updatePaddings(scrollView, dp(context, 16));
        scrollView.addView(wrapper);
        view = scrollView;
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
    }
}
