/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.accounts.types;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.utils.network.Http;
import net.minecraft.client.session.Session;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class SessionTokenAccount extends Account<SessionTokenAccount> {
    private @Nullable String token;
    public SessionTokenAccount(String sessionToken) {
        super(AccountType.SessionToken, sessionToken);
        this.token = sessionToken;
    }

    @Override
    public boolean fetchInfo() {
        token = auth();
        return token != null;
    }

    @Override
    public boolean login() {
        if (token == null) return false;

        super.login();
        cache.loadHead();

        setSession(new Session(cache.username, UndashedUuid.fromStringLenient(cache.uuid), token, Optional.empty(), Optional.empty(), Session.AccountType.MSA));
        return true;
    }

    private @Nullable String auth() {
        // Profile
        MicrosoftLogin.ProfileResponse profileRes = Http.get("https://api.minecraftservices.com/minecraft/profile")
            .bearer(token)
            .sendJson(MicrosoftLogin.ProfileResponse.class);

        name = profileRes.name;
        cache.username = profileRes.name;
        cache.uuid = profileRes.id;

        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SessionTokenAccount)) return false;
        return ((SessionTokenAccount) o).name.equals(this.name);
    }
}
