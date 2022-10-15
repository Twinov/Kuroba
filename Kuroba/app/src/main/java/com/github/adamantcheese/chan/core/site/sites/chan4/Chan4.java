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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import static com.github.adamantcheese.chan.core.net.NetUtils.createCookieParsingInterceptor;
import static com.github.adamantcheese.chan.core.net.NetUtils.loadWebviewCookies;
import static com.github.adamantcheese.chan.core.site.SiteSetting.Type.BOOLEAN;
import static com.github.adamantcheese.chan.core.site.SiteSetting.Type.OPTIONS;
import static com.github.adamantcheese.chan.core.site.common.CommonDataStructs.CaptchaType.CHAN4_CUSTOM;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getPreferences;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.SWF_THUMB_URL;
import static com.github.adamantcheese.chan.utils.HttpUrlUtilsKt.trimmedPathSegments;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.InternalSiteArchive;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.*;
import com.github.adamantcheese.chan.core.settings.primitives.*;
import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;
import com.github.adamantcheese.chan.core.settings.provider.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.core.site.*;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.*;
import com.github.adamantcheese.chan.core.site.common.FutabaSiteContentReader;
import com.github.adamantcheese.chan.core.site.http.*;
import com.github.adamantcheese.chan.core.site.parser.SiteContentReader;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.random.Random;
import okhttp3.*;

public class Chan4
        extends SiteBase {
    private SiteContentReader reader;

    public static final SiteUrlHandler URL_HANDLER = new SiteUrlHandler() {
        @SuppressWarnings("ConstantConditions")
        @Override
        public boolean respondsTo(@NonNull HttpUrl url) {
            return url.topPrivateDomain().equalsIgnoreCase(b.topPrivateDomain()) || url
                    .topPrivateDomain()
                    .equalsIgnoreCase(bSafe.topPrivateDomain());
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isThreadMode()) {
                String url = (loadable.board.workSafe ? bSafe : b)
                        .newBuilder()
                        .addPathSegment(loadable.boardCode)
                        .addPathSegment("thread")
                        .addPathSegment(String.valueOf(loadable.no))
                        .build()
                        .toString();
                if (postNo > 0 && loadable.no != postNo) {
                    url += "#p" + postNo;
                }
                return url;
            } else {
                return (loadable.board.workSafe ? bSafe : b)
                        .newBuilder()
                        .addPathSegment(loadable.boardCode)
                        .build()
                        .toString();
            }
        }

        @Override
        public Loadable resolveLoadable(Site site, HttpUrl url) {
            List<String> parts = trimmedPathSegments(url);

            if (!parts.isEmpty()) {
                String boardCode = parts.get(0);
                Board board = site.board(boardCode);
                if (board != null) {
                    if (parts.size() < 3) {
                        // Board mode
                        return Loadable.forCatalog(board);
                    } else {
                        // Thread mode
                        int no = -1;
                        try {
                            no = Integer.parseInt(parts.get(2));
                        } catch (NumberFormatException ignored) {
                        }

                        int post = -1;
                        String fragment = url.fragment();
                        if (fragment != null) {
                            int index = fragment.indexOf("p");
                            if (index >= 0) {
                                try {
                                    post = Integer.parseInt(fragment.substring(index + 1));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }

                        if (no >= 0) {
                            Loadable loadable = Loadable.forThread(board, no, "");
                            if (post >= 0) {
                                loadable.markedNo = post;
                            }

                            return loadable;
                        }
                    }
                }
            }

            return null;
        }
    };

    private final HttpUrl a = new HttpUrl.Builder().scheme("https").host("a.4cdn.org").build();
    private final HttpUrl i = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
    private final HttpUrl t = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
    private final HttpUrl s = new HttpUrl.Builder().scheme("https").host("s.4cdn.org").build();
    private final HttpUrl sys = new HttpUrl.Builder().scheme("https").host("sys.4chan.org").build();
    private final HttpUrl sysSafe = new HttpUrl.Builder().scheme("https").host("sys.4channel.org").build();
    private static final HttpUrl b = new HttpUrl.Builder().scheme("https").host("boards.4chan.org").build();
    private static final HttpUrl bSafe = new HttpUrl.Builder().scheme("https").host("boards.4channel.org").build();

    private final SiteEndpoints endpoints = new SiteEndpoints() {
        @Override
        public HttpUrl catalog(Board board) {
            return a.newBuilder().addPathSegment(board.code).addPathSegment("catalog.json").build();
        }

        @Override
        public HttpUrl thread(Loadable loadable) {
            return a
                    .newBuilder()
                    .addPathSegment(loadable.boardCode)
                    .addPathSegment("thread")
                    .addPathSegment(loadable.no + ".json")
                    .build();
        }

        @Override
        public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
            String imageFile = arg.get("tim") + "." + arg.get("ext");
            return i.newBuilder().addPathSegment(post.board.code).addPathSegment(imageFile).build();
        }

        @Override
        public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            if (spoiler) {
                HttpUrl.Builder image = s.newBuilder().addPathSegment("image");
                if (post.board.customSpoilers >= 0) {
                    int i = Random.Default.nextInt(post.board.customSpoilers) + 1;
                    image.addPathSegment("spoiler-" + post.board.code + i + ".png");
                } else {
                    image.addPathSegment("spoiler.png");
                }
                return image.build();
            } else {
                if ("swf".equals(arg.get("ext"))) {
                    return SWF_THUMB_URL;
                }
                return t.newBuilder().addPathSegment(post.board.code).addPathSegment(arg.get("tim") + "s.jpg").build();
            }
        }

        @Override
        public Pair<HttpUrl, PassthroughBitmapResult> icon(IconType icon, Map<String, String> arg) {
            HttpUrl.Builder iconBuilder = s.newBuilder().addPathSegment("image");

            switch (icon) {
                case COUNTRY_FLAG:
                    iconBuilder.addPathSegment("country");
                    iconBuilder.addPathSegment(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".gif");
                    break;
                case BOARD_FLAG:
                    String boardCode = arg.get("board_code").toLowerCase(Locale.ENGLISH);
                    String boardFlagCode = arg.get("board_flag_code").toLowerCase(Locale.ENGLISH);

                    iconBuilder.addPathSegment("flags");
                    iconBuilder.addPathSegment(boardCode);

                    if (spriteSetting.get()) {
                        // note: this is bad, but once this is cached it never makes a network request and is fine afterwards
                        try {
                            Response flagAlignments = NetUtils.applicationClient
                                    .newCall(new Request.Builder()
                                            .url("https://s.4cdn.org/image/flags/" + boardCode + "/flags.css")
                                            .build())
                                    .execute();

                            String alignmentsString;
                            try {
                                alignmentsString = flagAlignments.body().string();
                            } catch (Exception e) {
                                alignmentsString = "";
                            }
                            // for some reason, sometimes the css is returned with line separators; this deals with that weirdness
                            Pattern dimsPattern = Pattern.compile(
                                    "\\.bfl[\\s\\S ]*?\\{[\\s\\S ]*?width:.*?(\\d+)px;[\\s\\S ]*?height:.*?(\\d+)px;[\\s\\S ]*?\\}");
                            Matcher dimMatcher = dimsPattern.matcher(alignmentsString);
                            dimMatcher.find();

                            Pair<Integer, Integer> dims = new Pair<>(Math.abs(Integer.parseInt(dimMatcher.group(1))),
                                    Math.abs(Integer.parseInt(dimMatcher.group(2)))
                            );

                            Pattern flagPattern = Pattern.compile(
                                    "\\.bfl-"
                                            + boardFlagCode
                                            + "[\\s\\S ]*?\\{[\\s\\S ]*?background-position:.*?(\\d+)(?:px)? .*?(\\d+)(?:px)?[\\s\\S ]*?\\}",
                                    Pattern.CASE_INSENSITIVE
                            );
                            Matcher flagMatcher = flagPattern.matcher(alignmentsString);
                            flagMatcher.find();

                            Pair<Integer, Integer> origin = new Pair<>(Math.abs(Integer.parseInt(flagMatcher.group(1))),
                                    Math.abs(Integer.parseInt(flagMatcher.group(2)))
                            );
                            return new Pair<>(iconBuilder
                                    .addPathSegment("flags.png")
                                    .encodedFragment(flagMatcher.group())
                                    .build(), new NetUtilsClasses.CroppingBitmapResult(origin, dims));
                        } catch (Exception e) {
                            return new Pair<>(iconBuilder.addPathSegment(boardFlagCode + ".gif").build(),
                                    new PassthroughBitmapResult()
                            );
                        }
                    } else {
                        return new Pair<>(iconBuilder.addPathSegment(boardFlagCode + ".gif").build(),
                                new PassthroughBitmapResult()
                        );
                    }
                case SINCE4PASS:
                    iconBuilder.addPathSegment("minileaf.gif");
                    break;
            }

            return new Pair<>(iconBuilder.build(), new PassthroughBitmapResult());
        }

        @Override
        public HttpUrl boards() {
            return a.newBuilder().addPathSegment("boards.json").build();
        }

        @Override
        public HttpUrl pages(Board board) {
            return a.newBuilder().addPathSegment(board.code).addPathSegment("threads.json").build();
        }

        @Override
        public HttpUrl archive(Board board) {
            return (board.workSafe ? bSafe : b)
                    .newBuilder()
                    .addPathSegment(board.code)
                    .addPathSegment("archive")
                    .build();
        }

        @Override
        public HttpUrl reply(Loadable loadable) {
            return (loadable.board.workSafe ? sysSafe : sys)
                    .newBuilder()
                    .addPathSegment(loadable.boardCode)
                    .addPathSegment("post")
                    .build();
        }

        @Override
        public HttpUrl delete(Post post) {
            return (post.board.workSafe ? sysSafe : sys)
                    .newBuilder()
                    .addPathSegment(post.board.code)
                    .addPathSegment("imgboard.php")
                    .build();
        }

        @Override
        public HttpUrl report(Post post) {
            return (post.board.workSafe ? sysSafe : sys)
                    .newBuilder()
                    .addPathSegment(post.board.code)
                    .addPathSegment("imgboard.php")
                    .addQueryParameter("mode", "report")
                    .addQueryParameter("no", String.valueOf(post.no))
                    .build();
        }

        @Override
        public HttpUrl login() {
            return sys.newBuilder().addPathSegment("auth").build();
        }
    };

    private final SiteApi api = new SiteApi() {
        @Override
        public void boards(final ResponseResult<Boards> listener) {
            NetUtils.makeJsonRequest(endpoints.boards(),
                    listener,
                    new Chan4BoardsReader(Chan4.this),
                    NetUtilsClasses.ONE_DAY_CACHE
            );
        }

        @Override
        public void pages(Board board, ResponseResult<ChanPages> listener) {
            NetUtils.makeJsonRequest(endpoints().pages(board), new ResponseResult<ChanPages>() {
                @Override
                public void onFailure(Exception e) {
                    Logger.e(Chan4.this, "Failed to get pages for board " + board.code, e);
                    listener.onSuccess(new ChanPages());
                }

                @Override
                public void onSuccess(ChanPages result) {
                    listener.onSuccess(result);
                }
            }, new Chan4PagesReader(), NetUtilsClasses.NO_CACHE);
        }

        @Override
        public void archive(Board board, ResponseResult<InternalSiteArchive> archiveListener) {
            NetUtils.makeHTMLRequest(endpoints().archive(board),
                    new MainThreadResponseResult<>(archiveListener),
                    response -> {
                        List<InternalSiteArchive.ArchiveItem> items = new ArrayList<>();

                        Element table = response.getElementById("arc-list");
                        Element tableBody = table.getElementsByTag("tbody").first();
                        Elements trs = tableBody.getElementsByTag("tr");
                        for (Element tr : trs) {
                            Elements dataElements = tr.getElementsByTag("td");
                            String description = dataElements.get(1).text();
                            int id = Integer.parseInt(dataElements.get(0).text());
                            items.add(InternalSiteArchive.ArchiveItem.fromDescriptionId(description, id));
                        }

                        return InternalSiteArchive.fromItems(items);
                    },
                    NetUtilsClasses.NO_CACHE
            );
        }

        @Override
        public Call post(Loadable loadableWithDraft, final PostListener postListener) {
            return NetUtils.makeHttpCall(new Chan4ReplyCall(new MainThreadResponseResult<>(postListener),
                            loadableWithDraft
                    ),
                    Collections.singletonList(createCookieParsingInterceptor(c -> {
                        // in the event of a pass being already used, these will be immediately expired and you will be logged out
                        // due to a 4chan bug, posting on a worksafe board and getting this error will not correctly
                        // log-out a user; working around it is impossible due to okhttp's cookie parsing requiring
                        // a cookie's domain to match the request if the domain if the "domain" field of the cookie exists
                        // all 4chan cookies return a domain; they probably shouldn't but I can't do anything about that
                        if (StringUtils.isAnyIgnoreCase(c.name(), "pass_id", "pass_enabled")) {
                            List<Cookie> out = new ArrayList<>();
                            Collections.addAll(out,
                                    NetUtils.changeCookieDomain(c, sys.topPrivateDomain()),
                                    NetUtils.changeCookieDomain(c, sysSafe.topPrivateDomain())
                            );
                            return out;
                        } else {
                            return Collections.singletonList(c);
                        }
                    })),
                    postListener,
                    true
            );
        }

        @Override
        public boolean postRequiresAuthentication(Loadable loadableWithDraft) {
            return !isLoggedIn();
        }

        @Override
        public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
            final String CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
            if (isLoggedIn()) {
                return SiteAuthentication.fromNone();
            } else {
                switch (captchaType.get()) {
                    case V2JS:
                        return SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, b.toString());
                    case V2NOJS:
                        return SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, b.toString());
                    case CHAN4_CUSTOM:
                        HttpUrl.Builder urlBuilder = (loadableWithDraft.board.workSafe ? sysSafe : sys)
                                .newBuilder()
                                .addPathSegment("captcha")
                                .addQueryParameter("board", loadableWithDraft.board.code);
                        if (loadableWithDraft.isThreadMode()) {
                            urlBuilder.addQueryParameter("thread_id", String.valueOf(loadableWithDraft.no));
                        }
                        return SiteAuthentication.fromChan4Custom(urlBuilder.build().toString());
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }

        @Override
        public void delete(DeleteRequest deleteRequest, final ResponseResult<DeleteResponse> deleteListener) {
            NetUtils.makeHttpCall(new Chan4DeleteHttpCall(new MainThreadResponseResult<>(deleteListener),
                    deleteRequest
            ));
        }

        @Override
        public void login(String username, String password, final ResponseResult<LoginResponse> loginListener) {
            passUser.set(username);
            passPass.set(password);

            NetUtils.makeHttpCall(new Chan4PassHttpCall(new MainThreadResponseResult<>(loginListener),
                    new LoginRequest(Chan4.this, passUser.get(), passPass.get(), true)
            ), Collections.singletonList(createCookieParsingInterceptor(c -> {
                // for these two specific cookies, upon login set their expiration to never expire
                // 4chan defaults to 1 day, but an expired cookie value technically still works
                // also copy them to both 4chan.org and 4channel.org because hiro's real good at auth
                if (StringUtils.isAnyIgnoreCase(c.name(), "pass_id", "pass_enabled")) {
                    List<Cookie> out = new ArrayList<>();
                    Collections.addAll(out,
                            NetUtils
                                    .changeCookieDomain(c, sys.topPrivateDomain())
                                    .newBuilder()
                                    .expiresAt(Long.MAX_VALUE)
                                    .build(),
                            NetUtils
                                    .changeCookieDomain(c, sysSafe.topPrivateDomain())
                                    .newBuilder()
                                    .expiresAt(Long.MAX_VALUE)
                                    .build()
                    );
                    return out;
                } else {
                    return Collections.singletonList(c);
                }
            })));
        }

        @Override
        public void logout(final ResponseResult<LoginResponse> loginListener) {
            NetUtils.makeHttpCall(
                    new Chan4PassHttpCall(new MainThreadResponseResult<>(loginListener),
                            new LoginRequest(Chan4.this, "", "", false)
                    ),
                    Collections.singletonList(createCookieParsingInterceptor(c -> {
                        // same as login, but expire both cookies
                        if (StringUtils.isAnyIgnoreCase(c.name(), "pass_id", "pass_enabled")) {
                            List<Cookie> out = new ArrayList<>();
                            Collections.addAll(out,
                                    NetUtils.changeCookieDomain(c, sys.topPrivateDomain()),
                                    NetUtils.changeCookieDomain(c, sysSafe.topPrivateDomain())
                            );
                            return out;
                        } else {
                            return Collections.singletonList(c);
                        }
                    }))
            );
        }

        @Override
        public boolean isLoggedIn() {
            for (Cookie cookie : NetUtils.applicationClient.cookieJar().loadForRequest(sysSafe)) {
                if (cookie.name().equals("pass_id") && !cookie.value().isEmpty()) {
                    return true;
                }
            }
            for (Cookie cookie : NetUtils.applicationClient.cookieJar().loadForRequest(sys)) {
                if (cookie.name().equals("pass_id") && !cookie.value().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public LoginRequest getLoginDetails() {
            return new LoginRequest(Chan4.this, passUser.get(), passPass.get(), true);
        }

        @Override
        public List<Cookie> getCookies() {
            List<Cookie> ret = new ArrayList<>();
            ret.addAll(NetUtils.applicationClient.cookieJar().loadForRequest(sys));
            ret.addAll(NetUtils.applicationClient.cookieJar().loadForRequest(sysSafe));
            ret.addAll(NetUtils.applicationClient.cookieJar().loadForRequest(b));
            ret.addAll(NetUtils.applicationClient.cookieJar().loadForRequest(bSafe));
            return ret;
        }

        @Override
        public void clearCookies() {
            NetUtils.clearAllCookies(sys);
            NetUtils.clearAllCookies(sysSafe);
            NetUtils.clearAllCookies(b);
            NetUtils.clearAllCookies(bSafe);
        }
    };

    // Legacy settings that were global before
    private final StringSetting passUser;
    private final StringSetting passPass;

    public static OptionsSetting<CaptchaType> captchaType;
    public static BooleanSetting captchaAutosolve;
    private BooleanSetting spriteSetting;

    public Chan4() {
        // we used these before multisite, and lets keep using them.
        SettingProvider<Object> p = new SharedPreferencesSettingProvider(getPreferences());
        passUser = new StringSetting(p, "preference_pass_token", "");
        passPass = new StringSetting(p, "preference_pass_pin", "");
        icon().get(icon -> {});
        loadWebviewCookies(sys);
        loadWebviewCookies(sysSafe);
        loadWebviewCookies(b);
        loadWebviewCookies(bSafe);
    }

    @Override
    public void initializeSettings() {
        super.initializeSettings();

        captchaType = new OptionsSetting<>(settingsProvider,
                "preference_captcha_type_chan4",
                CaptchaType.class,
                CHAN4_CUSTOM
        );
        captchaAutosolve = new BooleanSetting(settingsProvider, "preference_captcha_autosolve", true);
        spriteSetting = new BooleanSetting(settingsProvider, "preference_sprite_map_chan4", false);
    }

    @Override
    public List<SiteSetting<?>> settings() {
        List<SiteSetting<?>> settings = new ArrayList<>();
        SiteSetting<?> captchaSetting = new SiteSetting<>("Captcha type",
                OPTIONS,
                captchaType,
                Arrays.asList("Javascript", "Noscript", "4chan Custom")
        );
        SiteSetting<?> captchaAutosolveSetting = new SiteSetting<>("Automatically trigger captcha autosolver",
                BOOLEAN,
                captchaAutosolve,
                Collections.emptyList()
        );
        SiteSetting<?> spriteMapSetting =
                new SiteSetting<>("Use sprite maps for board flags", BOOLEAN, spriteSetting, Collections.emptyList());
        settings.add(captchaSetting);
        settings.add(captchaAutosolveSetting);
        settings.add(spriteMapSetting);
        return settings;
    }

    @Override
    public String name() {
        return "4chan";
    }

    private final SiteIcon icon = SiteIcon.fromFavicon(HttpUrl.get("https://s.4cdn.org/image/favicon.ico"));

    @Override
    public SiteIcon icon() {
        return icon;
    }

    @Override
    public SiteUrlHandler resolvable() {
        return URL_HANDLER;
    }

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return true; // everything is supported
    }

    @Override
    public BoardsType boardsType() {
        // yes, boards.json
        return BoardsType.DYNAMIC;
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        switch (boardFeature) {
            case POSTING_IMAGE:
                // yes, we support image posting.
                return true;
            case POSTING_SPOILER:
                // depends if the board supports it.
                return board.spoilers;
            case ARCHIVE:
                // only some boards have local archives
                return board.archive;
            case FORCED_ANONYMOUS:
                // some boards (like /b/) disable the name field
                return board.forcedAnon;
            default:
                return false;
        }
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public SiteContentReader chanReader() {
        if (reader == null) {
            reader = new FutabaSiteContentReader();
        }
        return reader;
    }

    @Override
    public SiteApi api() {
        return api;
    }
}
