/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi.daos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UwUAccountDAO {
    private String id;
    private String combo;
    private String msaRefreshToken;
    private long issued;
    private long lastChecked;
    private String name;
    private String mcuuid;

    public UUID getMinecraftUUID() {
        return UUID.fromString(mcuuid);
    }
}
