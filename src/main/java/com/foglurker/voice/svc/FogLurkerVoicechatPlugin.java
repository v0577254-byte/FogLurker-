package com.foglurker.voice.svc;

import com.foglurker.FogLurkerMod;
import com.foglurker.voice.VoiceBridge;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/**
 * Simple Voice Chat discovers this class through the annotation. It is only ever loaded when the
 * voice chat mod is installed, so nothing else in Fog Lurker may reference it directly.
 */
@ForgeVoicechatPlugin
public final class FogLurkerVoicechatPlugin implements VoicechatPlugin {
    private SvcBackend backend;

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
