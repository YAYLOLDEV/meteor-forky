/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.gui.screens.accounts.AccountsScreen;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.accounts.types.ApiAccount;
import meteordevelopment.meteorclient.systems.mcacapi.gui.login.LoginScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.systems.proxies.ProxyType;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.gui.screens.accounts.AddApiAccountScreen.shouldContinue;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow
    @Final
    private DirectionalLayoutWidget grid;
    @Unique private ButtonWidget reconnectBtn;
    @Unique private double time = Modules.get().get(AutoReconnect.class).time.get() * 20;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/DirectionalLayoutWidget;refreshPositions()V", shift = At.Shift.BEFORE))
    private void addButtons(CallbackInfo ci) {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);

        if (autoReconnect.lastServerConnection != null && !autoReconnect.button.get()) {
            reconnectBtn = new ButtonWidget.Builder(Text.literal(getText()), button -> tryConnecting()).build();
            grid.add(reconnectBtn);

            grid.add(new ButtonWidget.Builder(Text.literal("Toggle Auto Reconnect"), button -> {
                autoReconnect.toggle();
                reconnectBtn.setMessage(Text.literal(getText()));
                time = autoReconnect.time.get() * 20;
            }).build());


        }

        this.addDrawableChild(
            new ButtonWidget.Builder(Text.literal("Change Account and Rejoin"), button -> {
                button.setMessage(Text.literal("Fetching Account..."));
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
                        if (!shouldContinue) {
                            button.setMessage(Text.literal("Rate-Limited!"));
                            return;
                        }
                        if (account == null) {
                            return;
                        }
                        account.login();

                        Accounts.get().add(account);
                        if (account.login()) Accounts.get().save();
                        tryConnecting();
                    } catch (Exception e) {
                        e.printStackTrace();

                    }
            }).build());
        this.addDrawableChild(
            new ButtonWidget.Builder(Text.literal("Change Account+Ip and Rejoin"), button -> {
                button.setMessage(Text.literal("Fetching Account..."));
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
                    if (!shouldContinue) {
                        button.setMessage(Text.literal("Rate-Limited!"));
                        return;
                    }
                    if (account == null) {
                        return;
                    }
                    account.login();

                    Accounts.get().add(account);
                    if (account.login()) Accounts.get().save();
                    button.setMessage(Text.literal("Fetching Proxy..."));
                    List<MyVeryCoolAndCustomAPI.ProxyDAO> dao = MyVeryCoolAndCustomAPI.getProxies();
                    if (dao == null) {
                        mc.setScreen(new LoginScreen());
                        return;
                    }
                    ;
                    Proxies.get().iterator().forEachRemaining(proxies -> {
                        if (proxies.name.get() == null || proxies.name.get().isBlank()) return;
                        dao.removeIf(p -> p.getId().toString().equals(proxies.name.get()));
                    });
                    MyVeryCoolAndCustomAPI.ProxyDAO random = dao.get(new Random().nextInt(0, dao.size() - 1));
                    Proxy pr = new Proxy.Builder().
                        name(random.getId().toString())
                        .address(random.getHost())
                        .port(random.getPort())
                        .type(switch (random.getProtocol()) {
                            case "socks4" -> ProxyType.Socks4;
                            case "http" -> ProxyType.Http;
                            case "socks5" -> ProxyType.Socks5;
                            default -> throw new IllegalStateException("Unexpected value: " + random.getProtocol());
                        })
                        .build();
                    Proxies.get().add(pr);
                    Proxies.get().setEnabled(pr, true);
                    tryConnecting();
                } catch (Exception e) {
                    e.printStackTrace();

                }
            }).position(0,25).build()
        );

    }

    private ApiAccount tryGetAccount(Set<String> ex) {
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

    private ApiAccount tryMultiple(int tries, Set<String> ex) {
        shouldContinue = true;
        for (int i = 0; i < tries; i++) {
            if (!shouldContinue) {
                return null;
            }
            ApiAccount account = tryGetAccount(ex);
            if (account != null) {
                return account;
            }
        }
        return null;
    }
    @Override
    public void tick() {
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (!autoReconnect.isActive() || autoReconnect.lastServerConnection == null) return;

        if (time <= 0) {
            tryConnecting();
        } else {
            time--;
            if (reconnectBtn != null) reconnectBtn.setMessage(Text.literal(getText()));
        }
    }

    @Unique
    private String getText() {
        String reconnectText = "Reconnect";
        if (Modules.get().isActive(AutoReconnect.class)) reconnectText += " " + String.format("(%.1f)", time / 20);
        return reconnectText;
    }

    @Unique
    private void tryConnecting() {
        var lastServer = Modules.get().get(AutoReconnect.class).lastServerConnection;
        ConnectScreen.connect(new TitleScreen(), MinecraftClient.getInstance(), lastServer.left(), lastServer.right(), false, null);
    }
}
