/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.accounts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.util.Util;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MicrosoftLogin {
    private MicrosoftLogin() {
    }

    public static class LoginData {
        public String mcToken;
        public String newRefreshToken;
        public String uuid, username;
        public boolean rateLimited;

        public static LoginData rateLimit() {
            LoginData data = new LoginData();
            data.rateLimited = true;
            return data;
        }

        public LoginData() {
        }

        public LoginData(String mcToken, String newRefreshToken, String uuid, String username) {
            this.mcToken = mcToken;
            this.newRefreshToken = newRefreshToken;
            this.uuid = uuid;
            this.username = username;
        }

        public boolean isGood() {
            return mcToken != null;
        }
    }

    private static class CachedLoginData {
        private final LoginData data;
        private final long expiryTime;

        public CachedLoginData(LoginData data, long ttlMs) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        public boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    // Cached XBL auth result (keyed by MS access token)
    private static class CachedXblAuth {
        final String xblToken;
        final String uhs;
        final long expiryTime;

        CachedXblAuth(String xblToken, String uhs, long ttlMs) {
            this.xblToken = xblToken;
            this.uhs = uhs;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    // Cached MC auth result (keyed by account UUID to avoid login_with_xbox)
    private static class CachedMcAuth {
        final String mcToken;
        final long expiryTime;

        CachedMcAuth(String mcToken, long ttlMs) {
            this.mcToken = mcToken;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }

    private static final String CLIENT_ID = "4673b348-3efa-4f6a-bbb6-34e141cdc638";
    private static final int PORT = 9675;

    // TTL constants
    private static final long LOGIN_DATA_TTL_MS = 23 * 60 * 60 * 1000L; // 23 hours (MC tokens expire in 24h)
    private static final long XBL_TOKEN_TTL_MS = 12 * 60 * 60 * 1000L; // 12 hours (conservative estimate)
    private static final long MC_TOKEN_TTL_MS = 23 * 60 * 60 * 1000L; // 23 hours

    // Caches
    private static final Map<String, CachedLoginData> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CachedXblAuth> XBL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CachedMcAuth> MC_CACHE = new ConcurrentHashMap<>(); // Keyed by account UUID

    private static HttpServer server;
    private static Consumer<String> callback;

    public static void getRefreshToken(Consumer<String> callback) {
        MicrosoftLogin.callback = callback;

        startServer();
        Util.getOperatingSystem()
                .open("https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID
                        + "&response_type=code&redirect_uri=http://127.0.0.1:" + PORT
                        + "&scope=XboxLive.signin%20offline_access&prompt=select_account");
    }

    public static LoginData login(String refreshToken) {
        return login(refreshToken, null);
    }

    /**
     * Login with refresh token, using accountUuid for MC token caching.
     * If accountUuid is provided, we can skip the expensive login_with_xbox call
     * when we have a valid cached MC token.
     */
    public static LoginData login(String refreshToken, String accountUuid) {
        // Check full login data cache first
        CachedLoginData cached = CACHE.get(refreshToken);
        if (cached != null && cached.isValid() && cached.data.isGood()) {
            return cached.data;
        }

        String oldRefreshToken = refreshToken;

        // Refresh access token - this is always needed
        AuthTokenResponse res = Http.post("https://login.live.com/oauth20_token.srf")
                .bodyForm("client_id=" + CLIENT_ID + "&refresh_token=" + refreshToken
                        + "&grant_type=refresh_token&redirect_uri=http://127.0.0.1:" + PORT)
                .sendJson(AuthTokenResponse.class);

        if (res == null)
            return new LoginData();

        String accessToken = res.access_token;
        refreshToken = res.refresh_token;

        // Check if we have a cached MC token for this account - skip
        // XBL/XSTS/login_with_xbox entirely
        if (accountUuid != null) {
            CachedMcAuth cachedMc = MC_CACHE.get(accountUuid);
            if (cachedMc != null && cachedMc.isValid()) {
                // Verify the token is still valid by checking profile
                ProfileResponse profileRes = Http.get("https://api.minecraftservices.com/minecraft/profile")
                        .bearer(cachedMc.mcToken)
                        .sendJson(ProfileResponse.class);

                if (profileRes != null) {
                    LoginData data = new LoginData(cachedMc.mcToken, refreshToken, profileRes.id, profileRes.name);
                    CACHE.put(oldRefreshToken, new CachedLoginData(data, LOGIN_DATA_TTL_MS));
                    return data;
                }
                // Token invalid, remove from cache and proceed with full auth
                MC_CACHE.remove(accountUuid);
            }
        }

        // Check XBL cache (keyed by MS access token)
        String xblToken;
        String uhs;
        CachedXblAuth cachedXbl = XBL_CACHE.get(accessToken);
        if (cachedXbl != null && cachedXbl.isValid()) {
            xblToken = cachedXbl.xblToken;
            uhs = cachedXbl.uhs;
        } else {
            // XBL auth
            XblXstsResponse xblRes = Http.post("https://user.auth.xboxlive.com/user/authenticate")
                    .bodyJson(
                            "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d="
                                    + accessToken
                                    + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}")
                    .sendJson(XblXstsResponse.class);

            if (xblRes == null)
                return new LoginData();

            xblToken = xblRes.Token;
            uhs = xblRes.DisplayClaims.xui[0].uhs;

            // Cache XBL result
            XBL_CACHE.put(accessToken, new CachedXblAuth(xblToken, uhs, XBL_TOKEN_TTL_MS));
        }

        // XSTS auth (no caching as it depends on XBL token which we cache)
        XblXstsResponse xstsRes = Http.post("https://xsts.auth.xboxlive.com/xsts/authorize")
                .bodyJson("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken
                        + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}")
                .sendJson(XblXstsResponse.class);

        if (xstsRes == null)
            return new LoginData();

        // Minecraft login_with_xbox - the expensive call we want to minimize
        McResponse mcRes = Http.post("https://api.minecraftservices.com/authentication/login_with_xbox")
                .bodyJson("{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsRes.Token + "\"}")
                .sendJson(McResponse.class);

        if (mcRes == null)
            return LoginData.rateLimit();

        // Check game ownership
        GameOwnershipResponse gameOwnershipRes = Http.get("https://api.minecraftservices.com/entitlements/mcstore")
                .bearer(mcRes.access_token)
                .sendJson(GameOwnershipResponse.class);

        if (gameOwnershipRes == null || !gameOwnershipRes.hasGameOwnership())
            return new LoginData();

        // Profile
        ProfileResponse profileRes = Http.get("https://api.minecraftservices.com/minecraft/profile")
                .bearer(mcRes.access_token)
                .sendJson(ProfileResponse.class);

        if (profileRes == null)
            return new LoginData();

        // Cache MC token by account UUID for future use
        if (profileRes.id != null) {
            MC_CACHE.put(profileRes.id, new CachedMcAuth(mcRes.access_token, MC_TOKEN_TTL_MS));
        }

        LoginData data = new LoginData(mcRes.access_token, refreshToken, profileRes.id, profileRes.name);
        CACHE.put(oldRefreshToken, new CachedLoginData(data, LOGIN_DATA_TTL_MS));
        return data;
    }

    /**
     * Clear all caches - useful when accounts are removed or tokens are known to be
     * invalid.
     */
    public static void clearCache() {
        CACHE.clear();
        XBL_CACHE.clear();
        MC_CACHE.clear();
    }

    /**
     * Clear cache for a specific account UUID.
     */
    public static void clearCacheForAccount(String accountUuid) {
        if (accountUuid != null) {
            MC_CACHE.remove(accountUuid);
        }
    }

    private static void startServer() {
        if (server != null)
            return;

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

            server.createContext("/", new Handler());
            server.setExecutor(MeteorExecutor.executor);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stopServer() {
        if (server == null)
            return;

        server.stop(0);
        server = null;

        callback = null;
    }

    private static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange req) throws IOException {
            if (req.getRequestMethod().equals("GET")) {
                // Login
                List<NameValuePair> query = URLEncodedUtils.parse(req.getRequestURI(), StandardCharsets.UTF_8);

                boolean ok = false;

                for (NameValuePair pair : query) {
                    if (pair.getName().equals("code")) {
                        handleCode(pair.getValue());

                        ok = true;
                        break;
                    }
                }

                if (!ok) {
                    writeText(req, "Cannot authenticate.");
                    callback.accept(null);
                } else
                    writeText(req, "You may now close this page.");
            }

            stopServer();
        }

        private void handleCode(String code) {
            AuthTokenResponse res = Http.post("https://login.live.com/oauth20_token.srf")
                    .bodyForm("client_id=" + CLIENT_ID + "&code=" + code
                            + "&grant_type=authorization_code&redirect_uri=http://127.0.0.1:" + PORT)
                    .sendJson(AuthTokenResponse.class);

            if (res == null)
                callback.accept(null);
            else
                callback.accept(res.refresh_token);
        }

        private void writeText(HttpExchange req, String text) throws IOException {
            OutputStream out = req.getResponseBody();

            req.sendResponseHeaders(200, text.length());

            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        }
    }

    private static class AuthTokenResponse {
        public String access_token;
        public String refresh_token;
    }

    private static class XblXstsResponse {
        public String Token;
        public DisplayClaims DisplayClaims;

        private static class DisplayClaims {
            private Claim[] xui;

            private static class Claim {
                private String uhs;
            }
        }
    }

    private static class McResponse {
        public String access_token;
    }

    private static class GameOwnershipResponse {
        private Item[] items;

        private static class Item {
            private String name;
        }

        private boolean hasGameOwnership() {
            boolean hasProduct = false;
            boolean hasGame = false;

            for (Item item : items) {
                if (item.name.equals("product_minecraft"))
                    hasProduct = true;
                else if (item.name.equals("game_minecraft"))
                    hasGame = true;
            }

            return hasProduct && hasGame;
        }
    }

    public static class ProfileResponse {
        public String id;
        public String name;
    }
}
