/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.uwuapi;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.systems.mcacapi.UwUAcData;
import meteordevelopment.meteorclient.systems.mcacapi.daos.UwUAccountDAO;
import meteordevelopment.meteorclient.systems.proxies.Proxies;
import meteordevelopment.meteorclient.systems.proxies.Proxy;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.player.TitleScreenCredits;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * API helper for UwU account system.
 * Communicates with the mcacapi service to fetch Minecraft accounts.
 */
public class MyVeryCoolAndCustomAPI {


    @Getter
    private static String apiBase = "https://mcacapi.lolyay.dev/api";
    @Getter
    private static String refreshToken = "";
    private static String accessToken = "";
    private static long accessTokenExpiry = 0;
    @Getter
    private static boolean signedIn = false;
    @Getter
    private static String name = "UNKNOWN";
    @Getter
    private static UUID userId = UUID.randomUUID();

    // DTO classes for API responses
    public static class MCAccountDAO {
        public String id;
        public String combo;
    }

    public static class RefreshTokenRequest {
        public String refreshToken;

        public RefreshTokenRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class RefreshTokenResponse {
        public String refreshToken;
        public String accessToken;
        public long expiry;
        public boolean success;
    }

    public static void setRefreshToken(String token) {
        refreshToken = token != null ? token : "";
        // Clear access token when refresh token changes
        accessToken = "";
        accessTokenExpiry = 0;
    }

    public static boolean isConfigured() {
        return !refreshToken.isEmpty();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ObjectId{
        private long timestamp, date;

        public String toString(){
            return String.valueOf(timestamp) + String.valueOf(date);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProxyDAO {
        private ObjectId id;

        private String protocol; // http, socks4, socks5

        private String host;

        private int port;

        private String fullUrl; // e.g., "http://1.2.3.4:80"

        private long lastValidated;

        private long addedAt;
    }

    public static List<ProxyDAO> getProxies(){
        if(!ensureValidToken()){
            return  null;
        }
        return Http.get(apiBase + "/proxies")
            .bearer(accessToken)
            .sendJson(new TypeToken<List<ProxyDAO>>(){}.getType());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccessTokenDAO {
        @SerializedName("access_token")
        private String accessToken;
        private String uuid;
        private String username;
    }


    /**
     * Ensures we have a valid access token, refreshing if necessary.
     *
     * @return true if we have a valid access token
     */
    public static boolean ensureValidToken() {
        if (!isConfigured()) {
            MeteorClient.LOG.error("[UwUAccountApi] Not configured - missing refresh token");
            return false;
        }

        // Check if current token is still valid (with 30 second buffer)
        if (!accessToken.isEmpty() && System.currentTimeMillis() < accessTokenExpiry - 30000) {
            return true;
        }

        // Need to refresh
        MeteorClient.LOG.info("[UwUAccountApi] Refreshing access token...");

        RefreshTokenResponse response = Http.post(apiBase + "/token/refresh")
                .bodyJson(new RefreshTokenRequest(refreshToken))
                .sendJson(RefreshTokenResponse.class);

        if (response == null || !response.success) {
            MeteorClient.LOG.error("[UwUAccountApi] Failed to refresh access token");
            return false;
        }

        // Update tokens
        accessToken = response.accessToken;
        accessTokenExpiry = response.expiry;
        refreshToken = response.refreshToken; // API returns new refresh token each time
        // 15 mins normally

        // Persist the new refresh token since it changes on every refresh
        try {
            UwUAcData.saveToken(refreshToken);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        signedIn = true;
        fetchUserDetails();
        MeteorClient.LOG.info("[UwUAccountApi] Access token refreshed, expires at {}", accessTokenExpiry);
        return true;
    }

    public static UwUAccountDAO getAccountByUUID(UUID uuid){
        return Http.get(apiBase + "/mcaccs/" + uuid.toString())
            .bearer(accessToken)
            .sendJson(UwUAccountDAO.class);
    }

    public static AccessTokenDAO getAccessTokenInfoByUUID(String uuid){
        return Http.get(apiBase + "/mcaccs/" + uuid + "/at")
            .bearer(accessToken)
            .sendJson(AccessTokenDAO.class);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class UpdateAccountDAO {
        private String token;
        private String username;
        private String mcuuid;
    }

    @Deprecated
    public static void updateUwUAccountInfo(String  uuid, MicrosoftLogin.LoginData data){
        Http.put(apiBase + "/mcaccs/" + uuid + "/refreshtoken")
            .bearer(accessToken)
            .bodyJson(new UpdateAccountDAO(data.newRefreshToken, data.username, data.uuid))
            .send();
    }

    private static void fetchUserDetails(){
        UserProfileRequest response = Http.get(apiBase + "/users/@me")
            .bearer(accessToken)
                .sendJson(UserProfileRequest.class);

        if (response == null) {
            MeteorClient.LOG.error("[UwUAccountApi] Failed to fetch user details");
            return;
        }

        MeteorClient.LOG.info("[UwUAccountApi] Logged in as: {}", response.getName());
        MeteorClient.LOG.info("[UwUAccountApi] User ID: {}", response.getId());
        userId = UUID.fromString(response.getId());
        name = response.getName();
        TitleScreenCredits.updateOnSignIn();

    }

    @Getter
    public static class RefreshTokenRequestDAO {
        private String refreshToken;
    }

    @Data
    public static class UserProfileRequest {
        private String name;
        private String id;
    }

    public static boolean quickLogin(String quickLoginCode){
        RefreshTokenRequestDAO response = Http.get(apiBase + "/token/quick/" + quickLoginCode)
            .sendJson(RefreshTokenRequestDAO.class);
        if(response == null)
            return  false;
        refreshToken = response.getRefreshToken();
        return ensureValidToken();
    }


    public static List<MCAccountDAO> getAllAccounts() {
        if (!ensureValidToken()) {
            return new ArrayList<>();
        }

        Type listType = new TypeToken<List<MCAccountDAO>>() {
        }.getType();
        List<MCAccountDAO> accounts = Http.get(apiBase + "/mcaccs")
                .bearer(accessToken)
                .sendJson(listType);

        if (accounts == null) {
            MeteorClient.LOG.error("[UwUAccountApi] Failed to fetch accounts");
            return new ArrayList<>();
        }

        MeteorClient.LOG.info("[UwUAccountApi] Fetched {} accounts from API", accounts.size());
        return accounts;
    }
    public static void deleteAccount(String apiAccountId) {
        if(!ensureValidToken())
            return;
        Http.delete(apiBase + "/mcaccs/" + apiAccountId)
                .bearer(accessToken)
                .send();
    }

    /**
     * Gets a random account from the API that is not already in the provided set of
     * IDs.
     *
     * @param existingIds Set of account IDs that are already added
     * @return A random available account, or null if none available
     */
    @Nullable
    public static MCAccountDAO getRandomAvailable(Set<String> existingIds) {
        List<MCAccountDAO> allAccounts = getAllAccounts();

        // Filter out already-added accounts
        List<MCAccountDAO> available = allAccounts.stream()
                .filter(acc -> !existingIds.contains(acc.id))
                .toList();

        if (available.isEmpty()) {
            MeteorClient.LOG.warn("[UwUAccountApi] No available accounts (all {} are already added)",
                    allAccounts.size());
            return null;
        }

        // Pick random
        int index = ThreadLocalRandom.current().nextInt(available.size());
        MCAccountDAO selected = available.get(index);
        MeteorClient.LOG.info("[UwUAccountApi] Selected random account: {} ({} available)", selected.id,
                available.size());
        return selected;
    }

}
