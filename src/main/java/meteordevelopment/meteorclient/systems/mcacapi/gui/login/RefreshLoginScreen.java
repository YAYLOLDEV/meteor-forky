/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi.gui.login;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.mcacapi.TimeAgo;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;

import java.time.Duration;
import java.time.Instant;

public class RefreshLoginScreen extends WindowScreen {
    public RefreshLoginScreen() {
        super(GuiThemes.get(), "Login with your Refresh token");
        locked = false;
    }
    @Override
    public void initWidgets() {
        add(theme.label("Login with your Account Refresh token"));
        WTextBox refreshToken = add(theme.textBox("","!!RT!!8aed3ed8-10fe-4f84-9a68-27ed52a4235d!!S!!...!!END!!")).expandX().widget();
        refreshToken.setFocused(true);

        WHorizontalList l = add(theme.horizontalList()).expandWidgetX().expandX().widget();
        l.add(theme.label("Status: "));
        WLabel label = l.add(theme.label("Invalid Refresh Token!")).widget();

        refreshToken.action = () -> {
            if (refreshToken.get().contains("!!ISS!!") && refreshToken.get().contains("!!END!!")) {
                try {
                    long ts = Long.parseLong(refreshToken.get().split("!!ISS!!")[1].split("!!END!!")[0]);
                    Duration dur = Duration.between(Instant.ofEpochMilli(ts), Instant.now());
                    label.set("Refresh token was issued " + TimeAgo.format(dur));
                } catch (Exception e) { /* Invalid ISS */}
            } else {
                label.set("Invalid Refresh Token format!");
            }

        };

        add(theme.button("Login")).widget().action = () -> {
            if(refreshToken.get().isEmpty()){
                label.set("Refresh token cannot be empty!");
                return;
            }
            if(refreshToken.get().contains("!!AT!!")) {
                label.set("This is an Access token, please use a Refresh token!");
                return;
            }
            if(!refreshToken.get().contains("!!RT!!") || !refreshToken.get().contains("!!S!!") || !refreshToken.get().contains("!!END!!") || !refreshToken.get().contains("!!ISS!!")){
                label.set("Invalid Refresh token format!");
                return;
            }

            label.set("Logging in...");
            locked = true;
            MeteorExecutor.execute(() -> {
                MyVeryCoolAndCustomAPI.setRefreshToken(refreshToken.get());
                if(!MyVeryCoolAndCustomAPI.ensureValidToken()){
                    label.set("Invalid Refresh token!");
                    locked = false;
                    return;
                }
                label.set("Logged in!");
                locked = false;
                close();
            });
        };
    }
}
