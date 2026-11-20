package com.foglurker.voice.svc;

import com.foglurker.FogLurkerMod;
import com.foglurker.voice.VoiceBridge;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/**
 * Simple Voice Chat finds this class through the "voicechat" entrypoint in fabric.mod.json. It is only ever loaded when the
 * voice chat mod is installed, so nothing else in Fog Lurker may reference it directly.
 */

public final class FogLurkerVoicechatPlugin implements VoicechatPlugin {
    /** Set and cleared on the server thread, read on the voice thread. */
    private volatile SvcBackend backend;

    @Override
    public String getPluginId() {
        return FogLurkerMod.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        FogLurkerMod.LOGGER.info("Simple Voice Chat found: Fog Lurker can now use opted-in voice snippets");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            backend = new SvcBackend(event.getVoicechat());
            VoiceBridge.setBackend(backend);
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            VoiceBridge.setBackend(null);
            if (backend != null) {
                backend.shutdown();
                backend = null;
            }
        });
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            SvcBackend b = backend;
            if (b != null) {
                b.onMicrophone(event);
            }
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            SvcBackend b = backend;
            if (b != null) {
                b.onDisconnect(event.getPlayerUuid());
            }
        });
    }
}
