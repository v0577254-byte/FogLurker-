package com.foglurker.voice.svc;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.voice.AudioFx;
import com.foglurker.voice.SnippetStore;
import com.foglurker.voice.VoiceBridge;
import com.foglurker.voice.VoiceConsent;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Records opted-in players' speech into snippets, and plays stolen snippets from the creature. */
final class SvcBackend implements VoiceBridge.Backend {
    static final String CATEGORY = "foglurker";
    private static final int FRAME = 960;
    private static final int MAX_SAMPLES = SnippetStore.SAMPLE_RATE * 6;
    private static final int MIN_SAMPLES = (int) (SnippetStore.SAMPLE_RATE * 0.6);
    private static final long GAP_MS = 400;

    private final VoicechatServerApi api;
    private final Map<UUID, Recorder> recorders = new ConcurrentHashMap<>();
    private final Map<Integer, AudioPlayer> playing = new ConcurrentHashMap<>();
    /** Whose voice each playing clip is, so opting out can cut it off mid-sentence. */
    private final Map<AudioPlayer, UUID> playingOwner = new ConcurrentHashMap<>();
    /** Creatures whose clip is still loading, so two calls in a row cannot start two clips. */
    private final java.util.Set<Integer> loading = ConcurrentHashMap.newKeySet();
    /** Creatures told to stop while their clip was still loading: that clip must not start. */
    private final java.util.Set<Integer> cancelled = ConcurrentHashMap.newKeySet();

    SvcBackend(VoicechatServerApi api) {
        this.api = api;
        try {
            VolumeCategory category = api.volumeCategoryBuilder()
                    .setId(CATEGORY)
                    .setName("Fog Lurker")
                    .setDescription("Voices that are not who they seem")
                    .build();
            api.registerVolumeCategory(category);
        } catch (Throwable t) {
            FogLurkerMod.LOGGER.warn("Could not register the Fog Lurker voice volume category", t);
        }
    }

    // ------------------------------------------------------------------ recording

    void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        if (!sender.isConnected()) {
            return; // a late packet from someone who already left must not open a new recorder
        }
        UUID id = sender.getPlayer().getUuid();
        if (!VoiceConsent.isOptedIn(id)) {
            return; // never record anyone who has not opted in
        }
        if (stopped) {
            return;
        }
        Recorder recorder = recorders.computeIfAbsent(id, Recorder::new);
        if (stopped) {
            if (recorders.remove(id, recorder)) {
                recorder.close(); // shutdown ran in between
            }
            return;
        }
        if (!VoiceConsent.isOptedIn(id)) {
            // They opted out between the check above and here: nothing of theirs may be kept, not even in memory.
            if (recorders.remove(id, recorder)) {
                recorder.discard();
                recorder.close();
            }
            return;
        }
        recorder.accept(event.getPacket().getOpusEncodedData());
    }

    void onDisconnect(UUID id) {
        Recorder r = recorders.remove(id);
        if (r != null) {
            r.finish();
            r.close();
        }
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (Recorder r : recorders.values()) {
            r.finishIfIdle(now);
        }
    }

    @Override
    public void forget(UUID player) {
        playingOwner.forEach((clip, owner) -> {
            if (owner.equals(player)) {
                clip.stopPlaying();
            }
        });
        Recorder r = recorders.remove(player);
        if (r != null) {
            r.discard();
            r.close();
        }
    }

    /** Stopped for good: a late microphone packet must not open a new recorder (its decoder would leak). */
    private volatile boolean stopped;

    void shutdown() {
        stopped = true;
        recorders.values().forEach(Recorder::close);
        recorders.clear();
        playing.values().forEach(AudioPlayer::stopPlaying);
        playing.clear();
    }

    private final class Recorder {
        private final UUID owner;
        private OpusDecoder decoder;
        private short[] buffer = new short[SnippetStore.SAMPLE_RATE * 2];
        private int size;
        private long lastPacket;
        private boolean active;
        private boolean capturing;
        /** Closed for good (opt-out, disconnect, shutdown): a late microphone packet must not reopen it. */
        private boolean closed;

        Recorder(UUID owner) {
            this.owner = owner;
        }

        synchronized void accept(byte[] opus) {
            if (closed) {
                return;
            }
            long now = System.currentTimeMillis();
            if (opus == null || opus.length == 0) {
                finish(); // end of transmission
                return;
            }
            if (active && now - lastPacket > GAP_MS) {
                finish();
            }
            if (!active) {
                active = true;
                capturing = ThreadLocalRandom.current().nextDouble() < FogLurkerConfig.COMMON.recordChance.get();
                if (capturing) {
                    if (decoder == null || decoder.isClosed()) {
                        decoder = api.createDecoder();
                    }
                    decoder.resetState();
                }
            }
            lastPacket = now;
            if (!capturing) {
                return;
            }
            short[] pcm = decoder.decode(opus);
            if (pcm == null) {
                return;
            }
            if (size + pcm.length > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + pcm.length));
            }
            System.arraycopy(pcm, 0, buffer, size, pcm.length);
            size += pcm.length;
            if (size >= MAX_SAMPLES) {
                finish();
            }
        }

        synchronized void finishIfIdle(long now) {
            if (active && now - lastPacket > GAP_MS) {
                finish();
            }
        }

        synchronized void finish() {
            if (capturing && size >= MIN_SAMPLES) {
                short[] clip = AudioFx.trimSilence(Arrays.copyOf(buffer, size), 300);
                if (clip.length >= MIN_SAMPLES && AudioFx.rms(clip, 0, clip.length) > 350) {
                    SnippetStore.saveAsync(owner, clip);
                }
            }
            discard();
        }

        synchronized void discard() {
            size = 0;
            active = false;
            capturing = false;
        }

        synchronized void close() {
            closed = true;
            size = 0;
            active = false;
            capturing = false;
            if (decoder != null && !decoder.isClosed()) {
                decoder.close();
            }
        }
    }

    // ------------------------------------------------------------------ playback

    @Override
    public boolean speak(Entity source, UUID voiceOwner, AudioFx.Style style, boolean splice) {
        AudioPlayer current = playing.get(source.getId());
        if (current != null && !current.isStopped()) {
            return false; // already talking
        }
        int entityId = source.getId();
        // Wrapped here on the server thread; the loading thread only checks a flag on it.
        var where = api.fromEntity(source);
        if (stopped || !loading.add(entityId)) {
            return false; // shutting down, or a clip for it is already on its way
        }
        cancelled.remove(entityId); // a stop meant for an earlier clip must not cancel this one
        SnippetStore.io().execute(() -> {
            try {
                short[] pcm;
                if (splice) {
                    List<short[]> parts = new ArrayList<>();
                    int n = 2 + ThreadLocalRandom.current().nextInt(2);
                    for (int i = 0; i < n; i++) {
                        parts.add(SnippetStore.loadRandom(voiceOwner));
                    }
                    pcm = AudioFx.splice(parts);
                    if (pcm.length < FRAME * 10) {
                        pcm = SnippetStore.loadRandom(voiceOwner);
                    }
                } else {
                    pcm = SnippetStore.loadRandom(voiceOwner);
                }
                if (pcm == null || pcm.length == 0 || source.isRemoved()) {
                    return;
                }
                pcm = AudioFx.apply(pcm, style);
                short[] framed = Arrays.copyOf(pcm, ((pcm.length + FRAME - 1) / FRAME) * FRAME);

                EntityAudioChannel channel = api.createEntityAudioChannel(UUID.randomUUID(), where);
                if (channel == null) {
                    return;
                }
                channel.setCategory(CATEGORY);
                channel.setDistance(FogLurkerConfig.COMMON.voiceDistance.get());
                OpusEncoder encoder = api.createEncoder();
                AudioPlayer player;
                try {
                    player = api.createAudioPlayer(channel, encoder, framed);
                } catch (RuntimeException e) {
                    encoder.close();
                    throw e;
                }
                player.setOnStopped(() -> {
                    encoder.close();
                    playing.remove(entityId, player);
                    playingOwner.remove(player);
                });
                if (!VoiceConsent.isOptedIn(voiceOwner) || stopped || cancelled.contains(entityId)) {
                    encoder.close();
                    return; // opted out while the clip was loading, or voice chat is shutting down
                }
                playingOwner.put(player, voiceOwner);
                playing.put(entityId, player);
                if (cancelled.contains(entityId)) {
                    playing.remove(entityId, player); // stopped in the instant before it would have started
                    playingOwner.remove(player);
                    encoder.close();
                    return;
                }
                try {
                    player.startPlaying();
                    if (cancelled.contains(entityId)) {
                        player.stopPlaying(); // told to stop while it was starting
                    }
                } catch (RuntimeException e) {
                    playing.remove(entityId, player);
                    playingOwner.remove(player);
                    encoder.close();
                    throw e;
                }
                if (!VoiceConsent.isOptedIn(voiceOwner)) {
                    player.stopPlaying(); // they opted out in the instant before it started
                }
            } catch (Throwable t) {
                FogLurkerMod.LOGGER.warn("Fog Lurker could not play a voice snippet", t);
            } finally {
                cancelled.remove(entityId);
                loading.remove(entityId);
            }
        });
        return true;
    }

    @Override
    public void stop(Entity source) {
        if (loading.contains(source.getId())) {
            cancelled.add(source.getId()); // its clip is still loading: it will not start
        }
        AudioPlayer p = playing.remove(source.getId());
        if (p != null) {
            p.stopPlaying();
        }
    }
}
