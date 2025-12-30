package meteordevelopment.meteorclient.utils;

import java.util.Random;

public class RandomStringGen {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random(); // reused

    public static String generate() {
        StringBuilder sb = new StringBuilder(6);

        for (int i = 0; i < 3; i++)
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        sb.append('-');
        for (int i = 0; i < 3; i++)
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));

        return sb.toString();
    }

}
