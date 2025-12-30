/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi;

import java.time.Duration;

public class TimeAgo {

    public static String format(Duration duration) {
        long seconds = duration.getSeconds();

        if (seconds < 5) {
            return "just now";
        }

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }
        if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        }
        if (minutes > 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        }

        return seconds + " second" + (seconds == 1 ? "" : "s") + " ago";
    }
}
