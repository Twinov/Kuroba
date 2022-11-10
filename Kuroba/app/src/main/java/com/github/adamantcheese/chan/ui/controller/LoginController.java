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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.*;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.features.html_styling.StyledHtml;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

public class LoginController
        extends Controller {
    private CrossfadeView crossfadeView;
    private TextView errors;
    private Button button;
    private EditText inputToken;
    private EditText inputPin;
    private TextView authenticated;

    private final Site site;

    public LoginController(Context context, Site site) {
        super(context);
        this.site = site;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_pass);

        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_pass, null);
        crossfadeView = view.findViewById(R.id.crossfade);
        errors = view.findViewById(R.id.errors);
        button = view.findViewById(R.id.button);
        TextView bottomDescription = view.findViewById(R.id.bottom_description);
        inputToken = view.findViewById(R.id.input_token);
        inputPin = view.findViewById(R.id.input_pin);
        authenticated = view.findViewById(R.id.authenticated);

        button.setOnClickListener((v) -> {
            authBefore();
            if (site.api().isLoggedIn()) {
                site.api().logout(new ResponseResult<LoginResponse>() {
                    @Override
                    public void onFailure(Exception e) {
                        authFail(getString(R.string.setting_pass_error_logout));
                        button.setText(R.string.setting_pass_logout);
                        authAfter();
                    }

                    @Override
                    public void onSuccess(LoginResponse loginResponse) {
                        if (loginResponse.isSuccess()) {
                            crossfadeView.toggle(true, true);
                            button.setText(R.string.submit);
                        } else {
                            authFail(loginResponse.getMessage());
                            button.setText(R.string.setting_pass_logout);
                        }

                        authAfter();
                    }
                });
            } else {
                site.api().login(
                        inputToken.getText().toString(),
                        inputPin.getText().toString(),
                        new ResponseResult<LoginResponse>() {
                            @Override
                            public void onFailure(Exception e) {
                                authFail(getString(R.string.setting_pass_error_login));
                                button.setText(R.string.submit);
                                authAfter();
                            }

                            @Override
                            public void onSuccess(LoginResponse loginResponse) {
                                if (loginResponse.isSuccess()) {
                                    crossfadeView.toggle(false, true);
                                    button.setText(R.string.setting_pass_logout);
                                    authenticated.setText(loginResponse.getMessage());
                                } else {
                                    authFail(loginResponse.getMessage());
                                    button.setText(R.string.submit);
                                }

                                authAfter();
                            }
                        }
                );
            }

            errors.setText(null);
            errors.setVisibility(GONE);
        });

        CharSequence bottomDesc =
                StyledHtml.fromHtml(getString(R.string.setting_pass_bottom_description), null);
        bottomDescription.setText(bottomDesc);
        bottomDescription.setMovementMethod(LinkMovementMethod.getInstance());

        LoginRequest loginDetails = site.api().getLoginDetails();
        inputToken.setText(loginDetails.user);
        inputPin.setText(loginDetails.pass);

        boolean loggedIn = site.api().isLoggedIn();
        if (loggedIn) {
            button.setText(R.string.setting_pass_logout);
        }
        crossfadeView.toggle(!loggedIn, false);
    }

    private void authBefore() {
        hideKeyboard(view);
        inputToken.setEnabled(false);
        inputPin.setEnabled(false);
        button.setEnabled(false);
        button.setText(R.string.loading);
    }

    private void authFail(String message) {
        errors.setText(message);
        errors.setVisibility(VISIBLE);
        BackgroundUtils.runOnMainThread(() -> {
            errors.setText(null);
            errors.setVisibility(GONE);
        }, 5000);
    }

    private void authAfter() {
        button.setEnabled(true);
        inputToken.setEnabled(true);
        inputPin.setEnabled(true);
    }
}
