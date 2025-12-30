/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.accounts.types;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.utils.misc.NbtException;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;
import net.minecraft.client.session.Session;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Account type that uses accounts from the API.
 * Each account is uniquely identified by its API ID.
 */
public class ApiAccount extends Account<ApiAccount> {
    private String apiAccountId; // UUID from the API
    private @Nullable String mcToken;
    private boolean isLoaded = false;

    public ApiAccount(String apiAccountId) {
        super(AccountType.ApiAccount, apiAccountId != null ? apiAccountId : "");
        this.apiAccountId = apiAccountId != null ? apiAccountId : "";
    }

    @Override
    public boolean fetchInfo() {
        if(isLoaded) return true;
        String refresh = MyVeryCoolAndCustomAPI.getAccountByUUID(UUID.fromString(apiAccountId)).getMsaRefreshToken();

        MicrosoftLogin.LoginData data = MicrosoftLogin.login(refresh);
        if (!data.isGood()) {
            MyVeryCoolAndCustomAPI.deleteAccount(apiAccountId);
            MeteorClient.LOG.error("[UwUAccount] Failed to authenticate with Microsoft");
            return false;
        }


        mcToken = data.mcToken;
        //IMPORTANT: Update the account info on the API
        MyVeryCoolAndCustomAPI.updateUwUAccountInfo(apiAccountId, data);

        cache.username = data.username;
        cache.uuid = data.uuid;
        cache.loadHead();

        MeteorClient.LOG.info("[UwUAccount] Authenticated as {} ({})", data.username, data.uuid);
        return true;
    }

    @Override
    public boolean login() {
        if (mcToken == null) {
            // Try to fetch info first
            if (!fetchInfo()) {
                return false;
            }
        }

        super.login();
        setSession(new Session(
                cache.username,
                UndashedUuid.fromStringLenient(cache.uuid),
                mcToken,
                Optional.empty(),
                Optional.empty(),
                Session.AccountType.MSA));

        return true;
    }

    /**
     * Gets the unique API account ID.
     */
    public String getApiAccountId() {
        return apiAccountId;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("type", type.name());
        tag.putString("apiAccountId", apiAccountId);
        tag.put("cache", cache.toTag());

        return tag;
    }

    @Override
    public ApiAccount fromTag(NbtCompound tag) {
        if (tag.getString("apiAccountId").isEmpty() || tag.getCompound("cache").isEmpty()) {
            throw new NbtException();
        }

        apiAccountId = tag.getString("apiAccountId");
        name = apiAccountId;
        cache.fromTag(tag.getCompound("cache"));
        isLoaded = false;

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ApiAccount other))
            return false;
        return this.apiAccountId.equals(other.apiAccountId);
    }

    @Override
    public int hashCode() {
        return apiAccountId.hashCode();
    }
}
