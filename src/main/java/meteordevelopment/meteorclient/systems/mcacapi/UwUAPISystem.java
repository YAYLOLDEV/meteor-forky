/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.mcacapi;

import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.uwuapi.MyVeryCoolAndCustomAPI;


public class UwUAPISystem extends System<UwUAPISystem> {
    public static final String name = "UwU-API-System";

    public UwUAPISystem() {
        super(name);
    }

    @Override
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
        if(UwUAcData.hasToken()) {
            try {
                MyVeryCoolAndCustomAPI.setRefreshToken(UwUAcData.loadToken());
                MyVeryCoolAndCustomAPI.ensureValidToken();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void save() {
        try {
            UwUAcData.saveToken(MyVeryCoolAndCustomAPI.getRefreshToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
