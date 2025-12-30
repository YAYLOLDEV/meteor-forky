/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi.gui.login;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;

public class QuickLoginScreen extends WindowScreen {
    public QuickLoginScreen() {
        super(GuiThemes.get(), "Login with your Quick Login Code");
        locked = false;
    }
    @Override
    public void initWidgets() {
        add(theme.label("Login with your Account Quick Login Code"));


        WTextBox quickLoginCode = add(theme.textBox("")).expandX().widget();
        WLabel label = add(theme.label("")).widget();
        label.visible = false;

        quickLoginCode.setFocused(true);


        add(theme.button("Login")).widget().action = () -> {
            if(quickLoginCode.get().isEmpty()){
                label.set("Quick Login Code cannot be empty!");
                label.visible = true;
                return;
            }

            label.set("Logging in...");
            label.visible = true;
            locked = true;
            MeteorExecutor.execute(() -> {

                if(!MyVeryCoolAndCustomAPI.quickLogin(quickLoginCode.get())){
                    label.set("Invalid Quick Login Code!");
                    label.visible = true;
                    locked = false;
                    return;
                }
                label.set("Logged in!");
                label.visible = true;
                locked = false;
                close();
            });
        };
    }
}
