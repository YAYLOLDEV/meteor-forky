/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.accounts.types;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.screens.accounts.AddApiAccountScreen;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.systems.mcacapi.daos.UwUAccountDAO;
import meteordevelopment.meteorclient.utils.misc.NbtException;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;
import net.minecraft.client.session.Session;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account type that uses accounts from the API.
 * Each account is uniquely identified by its API ID.
 */
public class ApiAccount extends Account<ApiAccount> {
    // Heap cache: API Account ID -> cached token data
    private static final Map<String, CachedApiToken> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = 23 * 60 * 60 * 1000L; // 23 hours

    private static class CachedApiToken {
        final String mcToken;
        final String username;
        final String uuid;
        final long expiryTime;

        CachedApiToken(String mcToken, String username, String uuid) {
            this.mcToken = mcToken;
            this.username = username;
            this.uuid = uuid;
            this.expiryTime = System.currentTimeMillis() + TOKEN_TTL_MS;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    private String apiAccountId; // UUID from the API
    private @Nullable String mcToken;
    private boolean isLoaded = false;

    public ApiAccount(String apiAccountId) {
        super(AccountType.ApiAccount, apiAccountId != null ? apiAccountId : "");
        this.apiAccountId = apiAccountId != null ? apiAccountId : "";
    }

    /**
     * Clear the token cache for a specific account.
     */
    public static void clearCacheForAccount(String accountId) {
        TOKEN_CACHE.remove(accountId);
        MicrosoftLogin.clearCacheForAccount(accountId);
    }

    /**
     * Clear all token caches.
     */
    public static void clearAllCache() {
        TOKEN_CACHE.clear();
        MicrosoftLogin.clearCache();
    }

    @Override
    public boolean fetchInfo() {
        if (isLoaded)
            return true;

        // Check heap cache first
        CachedApiToken cachedToken = TOKEN_CACHE.get(apiAccountId);
        if (cachedToken != null && cachedToken.isValid()) {
            mcToken = cachedToken.mcToken;
            cache.username = cachedToken.username;
            cache.uuid = cachedToken.uuid;
            cache.loadHead();
            isLoaded = true;
            MeteorClient.LOG.info("[UwUAccount] Using cached token for {} ({})", cachedToken.username,
                    cachedToken.uuid);
            return true;
        }
        MyVeryCoolAndCustomAPI.AccessTokenDAO accessTokenDAO = MyVeryCoolAndCustomAPI.getAccessTokenInfoByUUID(apiAccountId);
        if(accessTokenDAO == null)
            return false;
        if (accessTokenDAO.getAccessToken() == null)
            return false;
        MicrosoftLogin.LoginData data = new MicrosoftLogin.LoginData(accessTokenDAO.getAccessToken(), null, accessTokenDAO.getUuid(), accessTokenDAO.getUsername());
        if (!data.isGood()) {
            // Clear any stale cache entries for this account
            TOKEN_CACHE.remove(apiAccountId);
            MyVeryCoolAndCustomAPI.deleteAccount(apiAccountId);
            MeteorClient.LOG.error("[UwUAccount] Failed to authenticate with Microsoft");
            return false;
        }

        mcToken = data.mcToken;

        cache.username = data.username;
        cache.uuid = data.uuid;
        cache.loadHead();

        // Cache the token for this account
        TOKEN_CACHE.put(apiAccountId, new CachedApiToken(data.mcToken, data.username, data.uuid));
        isLoaded = true;

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
