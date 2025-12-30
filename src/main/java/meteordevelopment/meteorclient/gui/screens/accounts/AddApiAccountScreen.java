/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.accounts;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.accounts.types.ApiAccount;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;

import java.util.HashSet;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AddApiAccountScreen extends AddAccountScreen {
    public AddApiAccountScreen(GuiTheme theme, AccountsScreen parent) {
        super(theme, "Add API Account", parent);
    }
    private static WLabel label = null;



    @Override
    public void initWidgets() {

        add(theme.label("Fetching random account from API..."));
        label = add(theme.label("Please wait...")).widget();

        // Start async fetch
        locked = true;
        MeteorExecutor.execute(() -> {
            try {
                // Collect existing UwU account IDs
                Set<String> existingIds = new HashSet<>();
                for (var account : Accounts.get()) {
                    if (account instanceof ApiAccount uwu) {
                        existingIds.add(uwu.getApiAccountId());
                    }
                }

                // Get random available account
                int tries = 10;
                ApiAccount account = tryMultiple(tries, existingIds);
                if(account == null){
                    locked = false;
                    mc.execute(() -> {
                        clear();
                        add(theme.label("Error: Couldn't fetch Accounts"));
                        add(theme.label("Please try again or your token is expired."));
                        add(theme.button("Close")).widget().action = this::close;
                        add(theme.button("Retry")).widget().action = this::reload;
                    });
                    return;
                }


                AccountsScreen.addAccount(this, parent, account);

            } catch (Exception e) {
                e.printStackTrace();
                locked = false;
                mc.execute(() -> {
                    clear();
                    add(theme.label("Error fetching account: " + e.getMessage()));
                    add(theme.button("Close")).widget().action = this::close;
                });
            }
        });
    }
    private ApiAccount tryMultiple(int tries, Set<String > ex){
        for (int i = 0; i < tries; i++) {
            ApiAccount account = tryGetAccount(ex);
            if(account != null){
                return account;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        if(label != null && locked){
            switch (label.get()){
                case "Please wait...","Loading ....0":
                    label.set("Loading .....");
                    break;
                case "Loading .....":
                    label.set("Loading .0...");
                    break;
                case "Loading .0...":
                    label.set("Loading ..0..");
                    break;
                case "Loading ..0..":
                    label.set("Loading ...0.");
                    break;
                case "Loading ...0.":
                    label.set("Loading ....0");
                    break;
            }
        }
    }

    private ApiAccount tryGetAccount(Set<String > ex){
        MyVeryCoolAndCustomAPI.MCAccountDAO apiAccount = MyVeryCoolAndCustomAPI.getRandomAvailable(ex);

        if (apiAccount == null) {
            return null;
        }

        // Create and add the UwU account
        ApiAccount account = new ApiAccount(apiAccount.id);
        if (!account.fetchInfo()) {
            return null;
        }
        return account;
    }
}
