/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi.gui.login;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class LoginScreen extends WindowScreen {
    public LoginScreen() {
        super(GuiThemes.get(), "Login with your API Account");
    }

    @Override
    protected void init() {
        super.init();
        if(MyVeryCoolAndCustomAPI.isSignedIn())
            close();
    }

    @Override
    public void initWidgets() {
        MeteorExecutor.execute(() -> {
            WHorizontalList list = add(theme.horizontalList()).expandWidgetX().expandX().widget();
            list.add(theme.button("IRT Login")).expandX().widget().action = () -> {
                mc.setScreen(new RefreshLoginScreen());
            };
            list.add(theme.button("Quick Login")).expandX().widget().action = () -> {
                mc.setScreen(new QuickLoginScreen());
            };
        });

    }
}
