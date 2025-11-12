package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import org.lwjgl.openal.*;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTEfx.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * OpenAL EFX管理システム
 * Minecraftが既に使用しているOpenALコンテキストを利用
 */
public class OpenALManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private long device = NULL;
    private long context = NULL;
    private boolean efxSupported = false;

    // リバーブエフェクトスロット
    private int reverbSlot = 0;
    private int reverbEffect = 0;
    private boolean useEaxReverb = true; // EAXReverbが使えない場合は標準Reverbにフォールバック

    // Sourceごとのエフェクトスロット/エフェクト（発言者単位リバーブ用）
    private final ConcurrentHashMap<Integer, Integer> perSourceEffectSlots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> perSourceEffects = new ConcurrentHashMap<>();

    public void runOnMainThread(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(r);
        } else {
            r.run();
        }
    }

    // ローパスフィルタ（オクルージョン用：Direct Path）
    private final ConcurrentHashMap<Integer, Integer> sourceFilters = new ConcurrentHashMap<>();

    // ローパスフィルタ（リバーブ用：Auxiliary Send）
    private final ConcurrentHashMap<Integer, Integer> sourceReverbFilters = new ConcurrentHashMap<>();

    // 初期化状態
    private boolean initialized = false;

    /**
     * OpenALシステムを初期化
     * Minecraftが既にOpenALを初期化している場合、そのコンテキストを使用
     */
    public boolean initialize() {
        if (initialized) {
            LOGGER.warn("OpenAL Manager already initialized");
            return true;
        }

        try {
            // 現在のコンテキストを取得（Minecraftが初期化済み）
            context = alcGetCurrentContext();
            if (context != NULL) {
                device = alcGetContextsDevice(context);
                LOGGER.info("Using existing OpenAL context from Minecraft");
            } else {
                // Minecraftのコンテキストが無い場合、自分で初期化
                LOGGER.info("No existing OpenAL context, creating new one");
                device = alcOpenDevice((ByteBuffer) null);
                if (device == NULL) {
                    LOGGER.error("Failed to open OpenAL device");
                    return false;
                }

                ALCCapabilities deviceCaps = ALC.createCapabilities(device);
                context = alcCreateContext(device, (IntBuffer) null);
                if (context == NULL) {
                    LOGGER.error("Failed to create OpenAL context");
                    return false;
                }

                alcMakeContextCurrent(context);
                AL.createCapabilities(deviceCaps);
            }

            // EFX拡張の確認（デバイス拡張）
            efxSupported = org.lwjgl.openal.ALC.getCapabilities().ALC_EXT_EFX;

            if (efxSupported) {
                LOGGER.info("OpenAL EFX extension supported!");
                initializeEFX();
            } else {
                LOGGER.warn("OpenAL EFX extension not supported. Reverb and advanced effects will be disabled.");
            }

            // 基本設定
            alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
            alDopplerFactor(0.3f); // Doppler効果を軽減（ゲーム用）

            checkALError("initialize");

            initialized = true;
            LOGGER.info("OpenAL Manager initialized successfully");
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to initialize OpenAL Manager", e);
            return false;
        }
    }

    /**
     * EFXエクステンションを初期化
     */
    private void initializeEFX() {
        if (!efxSupported) {
            return;
        }

        try {
            // Auxiliary Effect Slot作成
            reverbSlot = alGenAuxiliaryEffectSlots();
            if (reverbSlot == 0) {
                LOGGER.error("Failed to create auxiliary effect slot");
                return;
            }

            // Reverbエフェクト作成
            reverbEffect = alGenEffects();
            if (reverbEffect == 0) {
                LOGGER.error("Failed to create reverb effect");
                return;
            }

            // EAXReverbタイプに設定（失敗したら標準Reverbへフォールバック）
            alEffecti(reverbEffect, AL_EFFECT_TYPE, AL_EFFECT_EAXREVERB);
            int err = alGetError();
            if (err != AL_NO_ERROR) {
                LOGGER.warn("EAXReverb not supported (err={}), falling back to AL_EFFECT_REVERB", err);
                alEffecti(reverbEffect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
                useEaxReverb = false;
            } else {
                useEaxReverb = true;
            }

            // デフォルトのリバーブパラメータ設定（中程度の部屋）
            setDefaultReverb();

            // エフェクトをスロットに接続
            alAuxiliaryEffectSloti(reverbSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);

            checkALError("initializeEFX");
            LOGGER.info("EFX initialized with EAXReverb effect");

        } catch (Exception e) {
            LOGGER.error("Failed to initialize EFX", e);
            efxSupported = false;
        }
    }

    /**
     * デフォルトのリバーブ設定（中程度の部屋）
     */
    private void setDefaultReverb() {
        if (!efxSupported || reverbEffect == 0) {
            return;
        }

        if (useEaxReverb) {
            // EAXReverb
            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN, 0.32f);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, 0.89f);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, 1.5f);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, 0.83f);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, 0.05f);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, 0.007f);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, 1.26f);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, 0.011f);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.994f);
        } else {
            // 標準Reverb
            alEffectf(reverbEffect, AL_REVERB_DENSITY, 1.0f);
            alEffectf(reverbEffect, AL_REVERB_DIFFUSION, 1.0f);
            alEffectf(reverbEffect, AL_REVERB_GAIN, 0.32f);
            alEffectf(reverbEffect, AL_REVERB_GAINHF, 0.89f);
            alEffectf(reverbEffect, AL_REVERB_DECAY_TIME, 1.5f);
            alEffectf(reverbEffect, AL_REVERB_DECAY_HFRATIO, 0.83f);
            alEffectf(reverbEffect, AL_REVERB_REFLECTIONS_GAIN, 0.05f);
            alEffectf(reverbEffect, AL_REVERB_REFLECTIONS_DELAY, 0.007f);
            alEffectf(reverbEffect, AL_REVERB_LATE_REVERB_GAIN, 1.26f);
            alEffectf(reverbEffect, AL_REVERB_LATE_REVERB_DELAY, 0.011f);
            alEffectf(reverbEffect, AL_REVERB_AIR_ABSORPTION_GAINHF, 0.994f);
        }

        checkALError("setDefaultReverb");
    }

    /**
     * リバーブパラメータを更新（簡易版）
     * @param decayTime 残響時間（秒）
     * @param density 密度（0.0～1.0）
     * @param diffusion 拡散（0.0～1.0）
     */
    public void updateReverb(float decayTime, float density, float diffusion) {
        if (!efxSupported || reverbEffect == 0) {
            return;
        }

        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, decayTime);
        alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, density);
        alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, diffusion);

        // エフェクトを再接続
        alAuxiliaryEffectSloti(reverbSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);

        checkALError("updateReverb");
    }

    /**
     * リバーブパラメータを更新（完全版・13パラメータ）
     * @param settings リバーブ設定
     */
    public void updateReverb(EFXReverbSettings settings) {
        if (!efxSupported || reverbEffect == 0) {
            return;
        }

        if (useEaxReverb) {
            // 13パラメータ（EAXReverb）
            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, settings.density);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, settings.diffusion);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN, settings.gain);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, settings.gainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, settings.gainLF);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, settings.decayTime);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, settings.decayHFRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, settings.decayLFRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, settings.reflectionsGain);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, settings.reflectionsDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, settings.lateReverbGain);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, settings.lateReverbDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, settings.roomRolloffFactor);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, settings.airAbsorptionGainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_HFREFERENCE, settings.hfReference);
            alEffectf(reverbEffect, AL_EAXREVERB_LFREFERENCE, settings.lfReference);
        } else {
            // 標準Reverbの対応パラメータに写像
            alEffectf(reverbEffect, AL_REVERB_DENSITY, settings.density);
            alEffectf(reverbEffect, AL_REVERB_DIFFUSION, settings.diffusion);
            alEffectf(reverbEffect, AL_REVERB_GAIN, settings.gain);
            alEffectf(reverbEffect, AL_REVERB_GAINHF, settings.gainHF);
            alEffectf(reverbEffect, AL_REVERB_DECAY_TIME, settings.decayTime);
            alEffectf(reverbEffect, AL_REVERB_DECAY_HFRATIO, settings.decayHFRatio);
            alEffectf(reverbEffect, AL_REVERB_REFLECTIONS_GAIN, settings.reflectionsGain);
            alEffectf(reverbEffect, AL_REVERB_REFLECTIONS_DELAY, settings.reflectionsDelay);
            alEffectf(reverbEffect, AL_REVERB_LATE_REVERB_GAIN, settings.lateReverbGain);
            alEffectf(reverbEffect, AL_REVERB_LATE_REVERB_DELAY, settings.lateReverbDelay);
            alEffectf(reverbEffect, AL_REVERB_ROOM_ROLLOFF_FACTOR, settings.roomRolloffFactor);
            alEffectf(reverbEffect, AL_REVERB_AIR_ABSORPTION_GAINHF, settings.airAbsorptionGainHF);
        }

        // エフェクトを再接続
        alAuxiliaryEffectSloti(reverbSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);

        checkALError("updateReverb(EFXReverbSettings)");
    }

    /**
     * OpenAL Sourceを作成
     * @return Source ID（0の場合は失敗）
     */
    public int createSource() {
        if (!initialized) {
            LOGGER.error("OpenAL Manager not initialized");
            return 0;
        }

        int sourceId = alGenSources();
        if (sourceId == 0) {
            LOGGER.error("Failed to create OpenAL source");
            return 0;
        }

        // 基本設定
        alSourcef(sourceId, AL_PITCH, 1.0f);
        alSourcef(sourceId, AL_GAIN, 1.0f);
        alSource3f(sourceId, AL_POSITION, 0, 0, 0);
        alSource3f(sourceId, AL_VELOCITY, 0, 0, 0);
        alSourcei(sourceId, AL_LOOPING, AL_FALSE);

        // 距離減衰パラメータ（デフォルト値：後でVolumeLevelに応じて更新）
        alSourcef(sourceId, AL_REFERENCE_DISTANCE, 3.0f);  // 基準距離: 3m
        alSourcef(sourceId, AL_MAX_DISTANCE, 30.0f);       // 最大距離: 30m（NORMAL相当）
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, 1.0f);      // 減衰係数: 1.0（標準）

        // リバーブの距離減衰は無効化（手動でWETを距離制御するため）
        alSourcef(sourceId, AL_ROOM_ROLLOFF_FACTOR, 0.0f);

        // 空気吸収（遠距離での高周波減衰）
        alSourcef(sourceId, AL_AIR_ABSORPTION_FACTOR, 0.1f);

        // Direct Filterを作成（オクルージョン用）
        if (efxSupported) {
            int filterId = alGenFilters();
            if (filterId != 0) {
                // ローパスフィルタに設定
                alFilteri(filterId, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                alFilterf(filterId, AL_LOWPASS_GAIN, 1.0f);      // 初期値: 透過100%
                alFilterf(filterId, AL_LOWPASS_GAINHF, 1.0f);    // 初期値: 高周波も100%

                sourceFilters.put(sourceId, filterId);

                // SourceにDirect Filterを適用
                alSourcei(sourceId, AL_DIRECT_FILTER, filterId);
            }
        }

        // Reverb Filterを作成（距離ベースWET調整用）
        if (efxSupported) {
            int reverbFilterId = alGenFilters();
            if (reverbFilterId != 0) {
                // ローパスフィルタに設定（ゲインでWET量を調整）
                alFilteri(reverbFilterId, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                alFilterf(reverbFilterId, AL_LOWPASS_GAIN, 1.0f);      // 初期値: WET 100%
                alFilterf(reverbFilterId, AL_LOWPASS_GAINHF, 1.0f);    // 高周波も100%

                sourceReverbFilters.put(sourceId, reverbFilterId);
                // SourceのAUX送信は、後でper-sourceのエフェクトスロット作成後に設定する
            }
        }

        checkALError("createSource");
        return sourceId;
    }

    /**
     * 発言者ごとのリバーブ設定を適用し、Sourceに専用エフェクトスロットを関連付ける
     */
    public void updateSourceReverb(int sourceId, EFXReverbSettings settings) {
        if (!efxSupported || !initialized || sourceId == 0) {
            LOGGER.warn("updateSourceReverb skipped: efxSupported={}, initialized={}, sourceId={}",
                       efxSupported, initialized, sourceId);
            return;
        }

        LOGGER.info("updateSourceReverb called: sourceId={}, lateReverbGain={}", sourceId, settings.lateReverbGain);

        // エフェクトスロット/エフェクトを確保
        int slot = perSourceEffectSlots.computeIfAbsent(sourceId, sid -> {
            int s = alGenAuxiliaryEffectSlots();
            LOGGER.info("Created auxiliary effect slot for source {}: slot={}", sid, s);
            checkALError("alGenAuxiliaryEffectSlots");
            return s;
        });
        int effect = perSourceEffects.computeIfAbsent(sourceId, sid -> {
            int e = alGenEffects();
            LOGGER.info("Created effect for source {}: effect={}", sid, e);
            checkALError("alGenEffects");
            return e;
        });
        if (slot == 0 || effect == 0) {
            LOGGER.error("Failed to create effect slot or effect: slot={}, effect={}", slot, effect);
            return;
        }

        LOGGER.info("Using effect slot={}, effect={} for source {}", slot, effect, sourceId);

        // エフェクトタイプ
        if (useEaxReverb) {
            alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_EAXREVERB);
        } else {
            alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
        }

        // パラメータ反映
        if (useEaxReverb) {
            alEffectf(effect, AL_EAXREVERB_DENSITY, settings.density);
            alEffectf(effect, AL_EAXREVERB_DIFFUSION, settings.diffusion);
            alEffectf(effect, AL_EAXREVERB_GAIN, settings.gain);
            alEffectf(effect, AL_EAXREVERB_GAINHF, settings.gainHF);
            alEffectf(effect, AL_EAXREVERB_GAINLF, settings.gainLF);
            alEffectf(effect, AL_EAXREVERB_DECAY_TIME, settings.decayTime);
            alEffectf(effect, AL_EAXREVERB_DECAY_HFRATIO, settings.decayHFRatio);
            alEffectf(effect, AL_EAXREVERB_DECAY_LFRATIO, settings.decayLFRatio);
            alEffectf(effect, AL_EAXREVERB_REFLECTIONS_GAIN, settings.reflectionsGain);
            alEffectf(effect, AL_EAXREVERB_REFLECTIONS_DELAY, settings.reflectionsDelay);
            alEffectf(effect, AL_EAXREVERB_LATE_REVERB_GAIN, settings.lateReverbGain);
            alEffectf(effect, AL_EAXREVERB_LATE_REVERB_DELAY, settings.lateReverbDelay);
            alEffectf(effect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, settings.roomRolloffFactor);
            alEffectf(effect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, settings.airAbsorptionGainHF);
            alEffectf(effect, AL_EAXREVERB_HFREFERENCE, settings.hfReference);
            alEffectf(effect, AL_EAXREVERB_LFREFERENCE, settings.lfReference);
        } else {
            alEffectf(effect, AL_REVERB_DENSITY, settings.density);
            alEffectf(effect, AL_REVERB_DIFFUSION, settings.diffusion);
            alEffectf(effect, AL_REVERB_GAIN, settings.gain);
            alEffectf(effect, AL_REVERB_GAINHF, settings.gainHF);
            alEffectf(effect, AL_REVERB_DECAY_TIME, settings.decayTime);
            alEffectf(effect, AL_REVERB_DECAY_HFRATIO, settings.decayHFRatio);
            alEffectf(effect, AL_REVERB_REFLECTIONS_GAIN, settings.reflectionsGain);
            alEffectf(effect, AL_REVERB_REFLECTIONS_DELAY, settings.reflectionsDelay);
            alEffectf(effect, AL_REVERB_LATE_REVERB_GAIN, settings.lateReverbGain);
            alEffectf(effect, AL_REVERB_LATE_REVERB_DELAY, settings.lateReverbDelay);
            alEffectf(effect, AL_REVERB_ROOM_ROLLOFF_FACTOR, settings.roomRolloffFactor);
            alEffectf(effect, AL_REVERB_AIR_ABSORPTION_GAINHF, settings.airAbsorptionGainHF);
        }

        // スロットにエフェクトを接続
        LOGGER.info("Connecting effect {} to slot {}", effect, slot);
        alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, effect);
        checkALError("alAuxiliaryEffectSloti");

        // SourceのAUX送信をこのスロットに向ける（既存のReverbフィルタを使用）
        Integer reverbFilterId = sourceReverbFilters.get(sourceId);
        if (reverbFilterId != null) {
            LOGGER.info("Setting AUX send: sourceId={}, slot={}, filter={}", sourceId, slot, reverbFilterId);
            alSource3i(sourceId, AL_AUXILIARY_SEND_FILTER, slot, 0, reverbFilterId);
            checkALError("AL_AUXILIARY_SEND_FILTER");
        } else {
            LOGGER.error("Reverb filter not found for source {}! AUX send not configured!", sourceId);
        }

        checkALError("updateSourceReverb");
        LOGGER.info("updateSourceReverb completed for source {}", sourceId);
    }

    /**
     * Sourceにオクルージョンフィルタを適用
     * @param sourceId Source ID
     * @param occlusion オクルージョン係数（0.0=完全遮蔽, 1.0=遮蔽なし）
     * @param gainHF 高周波減衰（0.0=完全減衰, 1.0=減衰なし）
     */
    public void setSourceOcclusion(int sourceId, float occlusion, float gainHF) {
        if (!efxSupported || !initialized) {
            return;
        }

        Integer filterId = sourceFilters.get(sourceId);
        if (filterId == null) {
            return;
        }

        // オクルージョンを適用: 全体ゲインと高周波ゲイン（極端なゼロはノイズ化を招くので下限を確保）
        float g = Math.max(0.05f, Math.min(1.0f, occlusion));
        float ghf = Math.max(0.05f, Math.min(1.0f, gainHF));
        alFilterf(filterId, AL_LOWPASS_GAIN, g);
        alFilterf(filterId, AL_LOWPASS_GAINHF, ghf);

        checkALError("setSourceOcclusion");
    }

    /**
     * VolumeLevelに応じた距離減衰パラメータを設定
     * @param sourceId Source ID
     * @param maxDistance 最大到達距離（VolumeLevel.getMaxDistance()から取得）
     */
    public void setSourceDistanceModel(int sourceId, float maxDistance) {
        if (!initialized || sourceId == 0) {
            return;
        }

        // 最大距離に応じてパラメータを調整
        float referenceDistance;
        float rolloffFactor;

        if (maxDistance <= 10f) {
            // WHISPER (5m), QUIET (10m)
            referenceDistance = 2.0f;
            rolloffFactor = 1.5f;  // 急激に減衰
        } else if (maxDistance <= 30f) {
            // NORMAL (30m)
            referenceDistance = 5.0f;
            rolloffFactor = 1.0f;  // 標準減衰
        } else if (maxDistance <= 60f) {
            // LOUD (60m)
            referenceDistance = 8.0f;
            rolloffFactor = 0.7f;  // やや緩やか
        } else {
            // SHOUT (100m)
            referenceDistance = 10.0f;
            rolloffFactor = 0.5f;  // 緩やか（遠くまで届く）
        }

        alSourcef(sourceId, AL_REFERENCE_DISTANCE, referenceDistance);
        alSourcef(sourceId, AL_MAX_DISTANCE, maxDistance);
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, rolloffFactor);

        checkALError("setSourceDistanceModel");
    }

    /**
     * 距離に応じたリバーブWETゲインを設定
     * @param sourceId Source ID
     * @param distance リスナーからの距離（m）
     * @param baseWetGain 基準WETゲイン（環境から計算された値）
     */
    public void setSourceReverbGain(int sourceId, float distance, float baseWetGain) {
        if (!efxSupported || !initialized) {
            return;
        }

        Integer reverbFilterId = sourceReverbFilters.get(sourceId);
        if (reverbFilterId == null) {
            return;
        }

        // 距離に応じたWETゲイン倍率を計算
        // 近距離（0-5m）：WET 15-35%（DRY優勢）
        // 中距離（5-20m）：WET 35-75%（バランス）
        // 遠距離（20m-）：WET 75-100%（WET優勢）
        float distanceMultiplier;
        if (distance < 5.0f) {
            // 近距離: 線形に0.15-0.35
            distanceMultiplier = 0.15f + (distance / 5.0f) * 0.20f;
        } else if (distance < 20.0f) {
            // 中距離: 線形に0.35-0.75
            distanceMultiplier = 0.35f + ((distance - 5.0f) / 15.0f) * 0.40f;
        } else {
            // 遠距離: 線形に0.75-1.0（最大30m）
            distanceMultiplier = 0.75f + Math.min((distance - 20.0f) / 30.0f, 0.25f);
        }

        // 最終WETゲイン = 基準ゲイン × 距離倍率
        float finalWetGain = baseWetGain * distanceMultiplier;
        finalWetGain = Math.max(0.0f, Math.min(1.0f, finalWetGain));

        // リバーブフィルタのゲインを更新
        alFilterf(reverbFilterId, AL_LOWPASS_GAIN, finalWetGain);

        checkALError("setSourceReverbGain");
    }

    /**
     * OpenAL Bufferを作成
     * @param samples PCMサンプル（モノラル、16bit）
     * @param sampleRate サンプルレート
     * @return Buffer ID（0の場合は失敗）
     */
    public int createBuffer(short[] samples, int sampleRate) {
        if (!initialized) {
            return 0;
        }

        int bufferId = alGenBuffers();
        if (bufferId == 0) {
            LOGGER.error("Failed to create OpenAL buffer");
            return 0;
        }

        // short[] → ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocateDirect(samples.length * 2);
        buffer.order(java.nio.ByteOrder.nativeOrder());
        buffer.asShortBuffer().put(samples);
        buffer.rewind();

        alBufferData(bufferId, AL_FORMAT_MONO16, buffer, sampleRate);

        checkALError("createBuffer");
        return bufferId;
    }

    /**
     * SourceにBufferをキューイング
     */
    public void queueBuffer(int sourceId, int bufferId) {
        if (!initialized) {
            return;
        }

        alSourceQueueBuffers(sourceId, bufferId);
        checkALError("queueBuffer");
    }

    /**
     * Sourceを再生
     */
    public void playSource(int sourceId) {
        if (!initialized) {
            return;
        }

        int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
        if (state != AL_PLAYING) {
            alSourcePlay(sourceId);
            checkALError("playSource");
        }
    }

    /**
     * Sourceの処理済みBufferを解放
     */
    public List<Integer> unqueueBuffers(int sourceId) {
        List<Integer> buffers = new ArrayList<>();
        if (!initialized) {
            return buffers;
        }

        int processed = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int bufferId = alSourceUnqueueBuffers(sourceId);
            if (bufferId != 0) {
                buffers.add(bufferId);
            }
        }

        return buffers;
    }

    /**
     * Sourceの位置を設定
     */
    public void setSourcePosition(int sourceId, float x, float y, float z) {
        if (!initialized) {
            return;
        }

        alSource3f(sourceId, AL_POSITION, x, y, z);
        checkALError("setSourcePosition");
    }

    /**
     * Listenerの位置を設定
     */
    public void setListenerPosition(float x, float y, float z) {
        if (!initialized) {
            return;
        }

        alListener3f(AL_POSITION, x, y, z);
        checkALError("setListenerPosition");
    }

    /**
     * Listenerの向きを設定
     * @param atX, atY, atZ 前方ベクトル
     * @param upX, upY, upZ 上方ベクトル
     */
    public void setListenerOrientation(float atX, float atY, float atZ, float upX, float upY, float upZ) {
        if (!initialized) {
            return;
        }

        float[] orientation = {atX, atY, atZ, upX, upY, upZ};
        alListenerfv(AL_ORIENTATION, orientation);
        checkALError("setListenerOrientation");
    }

    /**
     * Sourceを削除
     */
    public void deleteSource(int sourceId) {
        if (!initialized || sourceId == 0) {
            return;
        }

        alSourceStop(sourceId);
        alDeleteSources(sourceId);

        // 関連するDirect Filterを削除
        Integer filterId = sourceFilters.remove(sourceId);
        if (filterId != null && efxSupported) {
            alDeleteFilters(filterId);
        }

        // 関連するReverb Filterを削除
        Integer reverbFilterId = sourceReverbFilters.remove(sourceId);
        if (reverbFilterId != null && efxSupported) {
            alDeleteFilters(reverbFilterId);
        }

        // 専用エフェクト/スロットを削除
        Integer slot = perSourceEffectSlots.remove(sourceId);
        if (slot != null && slot != 0) {
            alDeleteAuxiliaryEffectSlots(slot);
        }
        Integer eff = perSourceEffects.remove(sourceId);
        if (eff != null && eff != 0) {
            alDeleteEffects(eff);
        }

        checkALError("deleteSource");
    }

    /**
     * Bufferを削除
     */
    public void deleteBuffer(int bufferId) {
        if (!initialized || bufferId == 0) {
            return;
        }

        alDeleteBuffers(bufferId);
        checkALError("deleteBuffer");
    }

    /**
     * シャットダウン
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        // EFXリソースの解放
        if (efxSupported) {
            if (reverbSlot != 0) {
                alDeleteAuxiliaryEffectSlots(reverbSlot);
            }
            if (reverbEffect != 0) {
                alDeleteEffects(reverbEffect);
            }
        }

        // コンテキストとデバイスは解放しない（Minecraftが管理）

        initialized = false;
        LOGGER.info("OpenAL Manager shut down");
    }

    /**
     * OpenALエラーチェック
     */
    private void checkALError(String operation) {
        int error = alGetError();
        if (error != AL_NO_ERROR) {
            LOGGER.error("OpenAL ERROR during {}: {} (code: {})", operation, getALErrorString(error), error);
            // スタックトレースも出力
            LOGGER.error("Stack trace:", new Exception("OpenAL Error Location"));
        } else {
            // 成功時もログ出力（デバッグ用）
            LOGGER.debug("OpenAL operation succeeded: {}", operation);
        }
    }

    /**
     * エラーコードを文字列に変換
     */
    private String getALErrorString(int error) {
        return switch (error) {
            case AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> "Unknown error: " + error;
        };
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isEfxSupported() {
        return efxSupported;
    }

    public int getReverbSlot() {
        return reverbSlot;
    }
}
