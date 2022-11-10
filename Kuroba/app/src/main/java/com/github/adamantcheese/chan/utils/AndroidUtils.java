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
package com.github.adamantcheese.chan.utils;

import static android.content.Context.*;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;

import android.annotation.SuppressLint;
import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.*;
import android.os.Build;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.PreferenceManager;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.google.android.material.snackbar.Snackbar;
import com.skydoves.balloon.Balloon;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.*;

public class AndroidUtils {
    private static final String CHAN_STATE_PREFS_NAME = "chan_state";

    @SuppressLint("StaticFieldLeak")
    private static Application application;

    public static void init(Application application) {
        if (AndroidUtils.application == null) {
            AndroidUtils.application = application;
        }
    }

    public static Resources getRes() {
        return application.getResources();
    }

    public static Context getAppContext() {
        return application;
    }

    @NonNull
    public static String getString(int res) {
        try {
            return getRes().getString(res);
        } catch (Exception e) {
            return e.getMessage() == null ? "UNKNOWN" : e.getMessage();
        }
    }

    public static String getString(int res, Object... formatArgs) {
        return getRes().getString(res, formatArgs);
    }

    public static String getQuantityString(int res, int quantity) {
        return getRes().getQuantityString(res, quantity, quantity);
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static SharedPreferences getAppState() {
        return getAppContext().getSharedPreferences(CHAN_STATE_PREFS_NAME, MODE_PRIVATE);
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        if (TextUtils.isEmpty(link)) {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
            return;
        }
        PackageManager pm = application.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        if (resolvedActivity == null) {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
        } else {
            boolean thisAppIsDefault = resolvedActivity.getPackageName().equals(application.getPackageName());
            if (!thisAppIsDefault) {
                openIntent(intent);
            } else {
                // Get all intents that match, and filter out this app
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());
                for (ResolveInfo info : resolveInfos) {
                    if (!info.activityInfo.packageName.equals(application.getPackageName())) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                        i.setPackage(info.activityInfo.packageName);
                        filteredIntents.add(i);
                    }
                }

                if (filteredIntents.size() > 0) {
                    // Create a chooser for the last app in the list, and add the rest with EXTRA_INITIAL_INTENTS that get placed above
                    Intent chooser = Intent.createChooser(filteredIntents.remove(filteredIntents.size() - 1), null);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, filteredIntents.toArray(new Intent[0]));
                    openIntent(chooser);
                } else {
                    showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
                }
            }
        }
    }

    /**
     * Tries to open a link in a custom tab.
     *
     * @param context Context for activity resolution
     * @param link    url to open
     */
    public static void openLinkInBrowser(Context context, String link) {
        if (TextUtils.isEmpty(link)) {
            showToast(context, R.string.open_link_failed);
            return;
        }
        try {
            // Hack that's sort of the same as openLink
            // The link won't be opened in a custom tab if this app is the default handler for that link.
            // Manually check if this app opens it instead of a custom tab, and use the logic of
            // openLink to avoid that and show a chooser instead.
            boolean openWithCustomTabs = true;
            Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            PackageManager pm = application.getPackageManager();
            ComponentName resolvedActivity = urlIntent.resolveActivity(pm);
            if (resolvedActivity != null) {
                openWithCustomTabs = !resolvedActivity.getPackageName().equals(application.getPackageName());
            }

            if (openWithCustomTabs) {
                //@formatter:off
                new CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                                .setToolbarColor(getAttrColor(context, R.attr.colorPrimary)).build())
                        .build()
                        .launchUrl(context, Uri.parse(link));
                //@formatter:on
            } else {
                openLink(link);
            }
        } catch (Exception e) {
            //any exception means we can't open the link
            showToast(context, R.string.open_link_failed, Toast.LENGTH_LONG);
        }
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getString(R.string.action_share));
        openIntent(chooser);
    }

    public static void openIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(application.getPackageManager()) != null) {
            application.startActivity(intent);
        } else {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
        }
    }

    public static int getThemeAttrColor(Theme theme, int attr) {
        return getAttrColor(new ContextThemeWrapper(application, theme.resValue), attr);
    }

    public static int getAttrColor(int themeId, int attr) {
        return getAttrColor(new ContextThemeWrapper(application, themeId), attr);
    }

    public static int getAttrColor(Context context, int attr) {
        return getAttrColor(context.getTheme(), attr);
    }

    public static int getAttrColor(Resources.Theme theme, int attr) {
        TypedArray typedArray = theme.obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    public static int getColor(int colorId) {
        return getRes().getColor(colorId);
    }

    public static int getContrastColor(int color) {
        double y = (299 * Color.red(color) + 587 * Color.green(color) + 114 * Color.blue(color)) / 1000f;
        return y >= 128.0 ? Color.BLACK : Color.WHITE;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static boolean isTablet() {
        return getRes().getBoolean(R.bool.is_tablet);
    }

    public static int getDimen(Context context, int dimen) {
        return context.getResources().getDimensionPixelSize(dimen);
    }

    public static File getAppDir() {
        return application.getFilesDir().getParentFile();
    }

    public static float dp(float dp) {
        return dp * getRes().getDisplayMetrics().density;
    }

    public static float dp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static float sp(float sp) {
        return sp * getRes().getDisplayMetrics().scaledDensity;
    }

    public static float sp(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }

    public static void requestKeyboardFocus(final View view) {
        getInputManager().showSoftInput(view, SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            getInputManager().hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void requestViewAndKeyboardFocus(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(true);
        if (view.requestFocus()) {
            getInputManager().showSoftInput(view, SHOW_IMPLICIT);
        }
    }

    // Clears selection popups and soft keyboards from whatever they'e currently on
    public static void clearAnySelectionsAndKeyboards(Context context) {
        try {
            View currentFocus = ((StartActivity) context).getContentView().getFocusedChild();
            if (currentFocus != null) {
                hideKeyboard(currentFocus);
                currentFocus.clearFocus();
            }
        } catch (Exception ignored) {}
    }

    public static void updatePaddings(View view, float all) {
        updatePaddings(view, all, all, all, all);
    }

    /**
     * Specify -1 to leave that padding the same as before.
     */
    public static void updatePaddings(View view, float left, float right, float top, float bottom) {
        float newLeft = left;
        if (newLeft < 0) {
            newLeft = view.getPaddingLeft();
        }

        float newRight = right;
        if (newRight < 0) {
            newRight = view.getPaddingRight();
        }

        float newTop = top;
        if (newTop < 0) {
            newTop = view.getPaddingTop();
        }

        float newBottom = bottom;
        if (newBottom < 0) {
            newBottom = view.getPaddingBottom();
        }

        view.setPadding((int) newLeft, (int) newTop, (int) newRight, (int) newBottom);
    }

    public static boolean removeFromParentView(View view) {
        if (view == null) return false;
        if (view.getParent() instanceof ViewGroup && ((ViewGroup) view.getParent()).indexOfChild(view) >= 0) {
            ((ViewGroup) view.getParent()).removeView(view);
            return true;
        } else {
            return false;
        }
    }

    public static boolean isConnected(int type) {
        NetworkInfo networkInfo = getConnectivityManager().getNetworkInfo(type);
        return networkInfo != null && networkInfo.isConnected();
    }

    public static ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * @return the display size of the full display, no subtractions
     */
    public static Point getDisplaySize() {
        Point displaySize = new Point();
        WindowManager windowManager = (WindowManager) application.getSystemService(Activity.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        return displaySize;
    }

    /**
     * @return the display size of the window, minus rendered nav/status bars
     */
    public static Point getWindowSize() {
        Point windowSize = new Point();
        Point windowSize2 = new Point();
        WindowManager windowManager = (WindowManager) application.getSystemService(Activity.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getCurrentSizeRange(windowSize, windowSize2);

        int windowWidth = getScreenOrientation() == ORIENTATION_PORTRAIT ? windowSize.x : windowSize2.y;
        int windowHeight = getScreenOrientation() == ORIENTATION_PORTRAIT ? windowSize2.x : windowSize.y;
        return new Point(windowWidth, windowHeight);
    }

    /**
     * These two methods get the screen size ignoring the current screen orientation.
     */
    public static int getMinScreenSize() {
        Point displaySize = getDisplaySize();
        return Math.min(displaySize.x, displaySize.y);
    }

    public static int getMaxScreenSize() {
        Point displaySize = getDisplaySize();
        return Math.max(displaySize.x, displaySize.y);
    }

    public static Window getWindow(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context).getWindow();
        } else {
            return null;
        }
    }

    private static InputMethodManager getInputManager() {
        return (InputMethodManager) application.getSystemService(INPUT_METHOD_SERVICE);
    }

    public static ClipboardManager getClipboardManager() {
        return (ClipboardManager) application.getSystemService(CLIPBOARD_SERVICE);
    }

    public static CharSequence getClipboardContent() {
        ClipData primary = getClipboardManager().getPrimaryClip();
        return primary != null ? primary.getItemAt(0).coerceToText(getAppContext()) : "";
    }

    public static void setClipboardContent(String label, String content) {
        getClipboardManager().setPrimaryClip(ClipData.newPlainText(label, content));
    }

    public static NotificationManager getNotificationManager() {
        return (NotificationManager) application.getSystemService(NOTIFICATION_SERVICE);
    }

    public static JobScheduler getJobScheduler() {
        return (JobScheduler) application.getSystemService(JOB_SCHEDULER_SERVICE);
    }

    public static AudioManager getAudioManager() {
        return (AudioManager) getAppContext().getSystemService(AUDIO_SERVICE);
    }

    public static boolean getDefaultMuteState() {
        return ChanSettings.videoDefaultMuted.get() && (ChanSettings.headsetDefaultMuted.get()
                || !getAudioManager().isWiredHeadsetOn());
    }

    public static void postToEventBus(Object message) {
        EventBus.getDefault().post(message);
    }

    public static boolean isAndroid10() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static int getScreenOrientation() {
        int screenOrientation = getAppContext().getResources().getConfiguration().orientation;
        if (screenOrientation != ORIENTATION_LANDSCAPE && screenOrientation != ORIENTATION_PORTRAIT) {
            throw new IllegalStateException("Illegal screen orientation value! value = " + screenOrientation);
        }

        return screenOrientation;
    }

    public static boolean isAprilFoolsDay() {
        if (PersistableChanState.noFunAllowed.get()) return false;
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MONTH) == Calendar.APRIL && calendar.get(Calendar.DAY_OF_MONTH) == 1;
    }

    /**
     * You'll need to add the following after you get this base popup:<br>
     * Arrow constraints (if needed)<br>
     * Arrow orientation<br>
     * The text for the hint (resID or String)<br>
     * A preference name, to only show this once<br><br>
     * You'll also need to call showAlignX, where X is opposite of the arrow orientation
     *
     * @param context The AppCompatActivity context for this hint.
     * @return A hint popup that still needs additional information.
     */
    public static Balloon.Builder getBaseToolTip(Context context) {
        return new Balloon.Builder(context)
                .setTextSize(14f)
                .setPadding(10)
                .setCornerRadius(2f)
                .setDismissWhenClicked(true)
                .setTextColor(getContrastColor(getAttrColor(context, R.attr.colorAccent)))
                .setBackgroundColor(getAttrColor(context, R.attr.colorAccent))
                .setLifecycleOwner((AppCompatActivity) context);
    }

    /**
     * Build and show a common snackbar, with all the appropriate stuff set.
     *
     * @param view        The anchoring view, with a coordinator layout somewhere as a parent
     * @param message     The message for the snackbar
     * @param actionResId The action resource ID for the action button, if any
     * @param action      The action for the action button, if any
     * @return the constructed snackbar
     */
    public static Snackbar buildCommonSnackbar(
            View view, String message, int actionResId, View.OnClickListener action
    ) {
        Snackbar s = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        // because we've got a weird mix of Material elements and other elements, these get messed up so they're set again
        s.setBackgroundTint(view.getContext().getResources().getColor(R.color.md_grey_850));
        s.setTextColor(Color.WHITE);
        s.setActionTextColor(view.getContext().getResources().getColor(R.color.md_grey_300));
        s.setGestureInsetBottomIgnored(true);
        if (actionResId != 0 && action != null) {
            s.setAction(actionResId, action);
        }
        if (BackgroundUtils.isInForeground()) {
            s.show();
        }
        return s;
    }

    public static Snackbar buildCommonSnackbar(View view, String message) {
        return buildCommonSnackbar(view, message, 0, null);
    }

    public static Snackbar buildCommonSnackbar(View view, int message, int actionResId, View.OnClickListener action) {
        return buildCommonSnackbar(view, getString(message), actionResId, action);
    }

    public static boolean supportsWebView() {
        try {
            CookieManager.getInstance();
        } catch (Exception e) {
            return false;
        }
        return application.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
    }
}
