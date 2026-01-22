package com.amnezia;

import net.fabricmc.api.ClientModInitializer;

public class AmneziaClientMod implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        AmneziaMod.debug("============================================================");
        AmneziaMod.debug("AMNEZIA CLIENT INITIALIZED");
        AmneziaMod.debug("============================================================");
    }
}