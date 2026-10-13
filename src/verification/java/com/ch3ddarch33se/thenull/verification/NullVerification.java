package com.ch3ddarch33se.thenull.verification;

import com.ch3ddarch33se.thenull.worldgen.WorldProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Local-only entrypoint; never packaged in the playable mod. */
public final class NullVerification implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (Boolean.getBoolean("thenull.benchmark")) PerformanceVerification.start(server);
            else NativeVerification.run(server, WorldProvider.WORLD);
        });
    }
}
