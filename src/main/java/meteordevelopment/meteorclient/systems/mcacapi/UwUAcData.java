/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi;

import meteordevelopment.meteorclient.utils.CryptUtil;
import meteordevelopment.meteorclient.utils.HashUtil;
import meteordevelopment.meteorclient.utils.RandomStringGen;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class UwUAcData {
    private static final boolean lite = true; // TO not spook some scary people "Why is it savign stuff in localstorage"

    private static final Path TOKEN_PATH = Path.of(System.getenv("APPDATA"), "ud", "DO_NOT_DELETE");
    private static final Path KEY_PATH = Path.of(System.getenv("LOCALAPPDATA"), "ud", "DO_NOT_DELETE");
    private static final Path SALT_PATH = Path.of(System.getenv("LOCALAPPDATA"), "udk", "DO_NOT_DELETE");

    public static void saveToken(String token) throws Exception {
        if(lite)
        {
            File file = new File("token.txt");
            if(!file.exists()){
                file.createNewFile();
            }
            Files.writeString(file.toPath(), token);
            return;
        }
        File tokenFile = TOKEN_PATH.toFile();
        File keyFile = KEY_PATH.toFile();
        File saltFile = SALT_PATH.toFile();

        // Create token file directory
        if (!tokenFile.exists()) {
            tokenFile.getParentFile().mkdirs();
            tokenFile.createNewFile();
        }

        // Create salt file with random data
        if (!saltFile.exists()) {
            byte[] dta = new byte[128];
            new SecureRandom().nextBytes(dta);
            String dh = Base64.getEncoder().encodeToString(dta);
            dh += RandomStringGen.generate();
            dh += UUID.randomUUID().toString();
            saltFile.getParentFile().mkdirs();
            saltFile.createNewFile();
            Files.writeString(SALT_PATH, dh, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        // Create key file with random bytes
        if (!keyFile.exists()) {
            byte[] dta = new byte[32];
            new SecureRandom().nextBytes(dta);
            keyFile.getParentFile().mkdirs();
            keyFile.createNewFile();
            Files.write(KEY_PATH, dta, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        byte[] encryptedData = CryptUtil.encrypt(token.getBytes(), deriveKey());
        Files.write(TOKEN_PATH, encryptedData, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    public static String loadToken() throws Exception {
        if(lite)
            return Files.exists(new File("token.txt").toPath()) ? Files.readString(new File("token.txt").toPath()) : null;

        File tokenFile = TOKEN_PATH.toFile();
        File keyFile = KEY_PATH.toFile();
        File saltFile = SALT_PATH.toFile();

        // Check if all required files exist
        if (!tokenFile.exists() || !keyFile.exists() || !saltFile.exists()) {
            return null;
        }

        byte[] encryptedData = Files.readAllBytes(TOKEN_PATH);
        if (encryptedData.length == 0) {
            return null;
        }

        byte[] decryptedData = CryptUtil.decrypt(encryptedData, deriveKey());
        return new String(decryptedData);
    }

    public static boolean hasToken() {
        if(lite)
            return Files.exists(new File("token.txt").toPath());

        return TOKEN_PATH.toFile().exists() &&
                KEY_PATH.toFile().exists() &&
                SALT_PATH.toFile().exists();
    }

    public static void deleteToken() throws Exception {
        Files.deleteIfExists(TOKEN_PATH);
    }

    private static byte[] deriveKey() throws Exception {
        byte[] k = Files.readAllBytes(KEY_PATH);
        String kh = Base64.getEncoder().encodeToString(k) + "__UWUCLIENT_DND_V:2__" + Files.readString(SALT_PATH);
        String eK = HashUtil.sha256(kh);
        return eK.getBytes(); // SHA-256 hex string = 64 chars = 64 bytes
    }
}
