package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio.NativeLibraryLoader;
import jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio.SteamAudioLibrary;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Steam Audio鬩幢ｽ｢繝ｻ・ｧ髯ｷ莉｣繝ｻ繝ｻ・ｽ繝ｻ・ｽ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ鬩搾ｽｵ繝ｻ・ｺ髯ｷ莨夲ｽｽ・ｱ髫ｨ・ｳ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ繝ｻ蠑ｱ繝ｻ
 * Phase 1: 鬮ｯ諞ｺ螻ｮ繝ｻ・ｽ繝ｻ・ｺ鬮ｫ・ｴ陝ｷ・｢繝ｻ・ｽ繝ｻ・ｬ鬯ｨ・ｾ繝ｻ・ｧ驛｢譎｢・ｽ・ｻ驕ｶ莨√・繝ｻ・ｹ隴寂・繝ｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ
 */
public class AudioPlayerSteamAudio {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Steam Audio鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｽ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ
    private Pointer context = null;
    private Pointer hrtf = null;
    private final ConcurrentHashMap<UUID, PlayerAudioSource> playerSources = new ConcurrentHashMap<>();

    // 鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ隨倥・ﾎ斐・・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ繝ｻ蠑ｱ繝ｻ
    private SourceDataLine outputLine;
    private Thread playbackThread;
    private volatile boolean running = false;
    private volatile double outputGain = 1.0;
    // Binaural effect compensation gain (approximately 15.6 dB)
    private static final float BINAURAL_COMPENSATION_GAIN = 6.0f;

    // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴弱・・ｽ・ｼ隴・搨・ｰ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴弱・・ｽ・ｧ繝ｻ・ｭ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴寂握縺狗ｹ晢ｽｻ繝ｻ・ｼ髯具ｽｹ繝ｻ・ｻ驍ｵ・ｺ陝ｶ・ｷ繝ｻ・ｹ隴擾ｽｴ郢晢ｽｻ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｹ繝ｻ・｢郢晢ｽｻ繝ｻ・ｼ驛｢譎｢・ｽ・ｻ
    private final AudioFormat audioFormat = new AudioFormat(
            AudioConstants.SAMPLE_RATE,
            16,
            2,  // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ陝ｲ・ｨ郢晢ｽｻ
            true,
            false
    );

    /**
     * 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬯ｯ・ｮ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｮ荵昴・郢晢ｽｻ
     */
    private class PlayerAudioSource {
        Vec3 position;
        final LinkedBlockingQueue<short[]> audioQueue = new LinkedBlockingQueue<>(50);
        Pointer binauralEffect = null;

        // Steam Audio鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴弱・・ｽ・ｼ隴∵腸・ｼ諛・ｽｹ譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻPLAudioBuffer鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｻ・ｻ繝ｻ・ｽ陞ｳ螢ｽ蜑ｲ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
        SteamAudioLibrary.IPLAudioBuffer inBuffer = null;
        SteamAudioLibrary.IPLAudioBuffer outBuffer = null;

        PlayerAudioSource(Pointer binauralEffect) {
            this.binauralEffect = binauralEffect;
            allocateBuffers();
        }

        private void allocateBuffers() {
            try {
                // 鬮ｯ・ｷ髣鯉ｽｨ繝ｻ・ｽ繝ｻ・･鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ・趣ｽ｣鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｵ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｡驛｢譎｢・ｽ・ｻ髯具ｽｹ繝ｻ・ｻ繝ｻ螳亥擠繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ IPLAudioBuffer鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｻ・ｻ繝ｻ・ｽ陞ｳ螢ｽ蜑ｲ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ
                inBuffer = new SteamAudioLibrary.IPLAudioBuffer();
                int result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(
                    context,
                    1,  // 鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴取得・ｽ・ｼ繝ｻ・ｱ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ
                    AudioConstants.FRAME_SIZE,
                    inBuffer.getPointer()
                );

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to allocate input buffer. Error code: {}", result);
                    return;
                }

                // 鬩幢ｽ｢隴取ｨ費ｽｺ繧会ｽｸ・ｺ郢晢ｽｻ繝ｻ・ｹ隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴弱・ﾂｧ繝ｻ譛ｱ雎ｪ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ髣・ｽｽ繝ｻ・ｮ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｷ・ｶ・趣ｽｨ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｣鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴取得・ｽ・ｳ繝ｻ・ｨ郢晢ｽｻ陝ｶ譎乗套郢晢ｽｻ繝ｻ・ｭ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬯ｮ・ｴ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ
                inBuffer.read();
                // Ensure buffer format is set explicitly
                inBuffer.format.channelLayoutType = SteamAudioLibrary.IPLChannelLayoutType.IPL_CHANNELLAYOUTTYPE_SPEAKERS;
                inBuffer.format.channelOrder = SteamAudioLibrary.IPLChannelOrder.IPL_CHANNELORDER_DEINTERLEAVED;
                inBuffer.format.numChannels = 1;
                inBuffer.format.numSamples = AudioConstants.FRAME_SIZE;
                inBuffer.format.sampleRate = AudioConstants.SAMPLE_RATE;
                inBuffer.write();

                LOGGER.info("Input buffer allocated: channels={}, samples={}, data={}",
                    inBuffer.format.numChannels, inBuffer.format.numSamples, inBuffer.data);

                if (inBuffer.data == null || Pointer.nativeValue(inBuffer.data) == 0) {
                    LOGGER.error("CRITICAL: Input buffer data pointer is NULL after iplAudioBufferAllocate!");
                }

                // 鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ・趣ｽ｣鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｵ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｡驛｢譎｢・ｽ・ｻ髯具ｽｹ繝ｻ・ｻ驍ｵ・ｺ陝ｶ・ｷ繝ｻ・ｹ隴擾ｽｴ郢晢ｽｻ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ IPLAudioBuffer鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｻ・ｻ繝ｻ・ｽ陞ｳ螢ｽ蜑ｲ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ
                outBuffer = new SteamAudioLibrary.IPLAudioBuffer();
                result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(
                    context,
                    2,  // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ
                    AudioConstants.FRAME_SIZE,
                    outBuffer.getPointer()
                );

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to allocate output buffer. Error code: {}", result);
                    return;
                }

                // 鬩幢ｽ｢隴取ｨ費ｽｺ繧会ｽｸ・ｺ郢晢ｽｻ繝ｻ・ｹ隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴弱・ﾂｧ繝ｻ譛ｱ雎ｪ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ髣・ｽｽ繝ｻ・ｮ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｷ・ｶ・趣ｽｨ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｣鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴取得・ｽ・ｳ繝ｻ・ｨ郢晢ｽｻ陝ｶ譎乗套郢晢ｽｻ繝ｻ・ｭ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬯ｮ・ｴ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ
                outBuffer.read();
                outBuffer.format.channelLayoutType = SteamAudioLibrary.IPLChannelLayoutType.IPL_CHANNELLAYOUTTYPE_SPEAKERS;
                outBuffer.format.channelOrder = SteamAudioLibrary.IPLChannelOrder.IPL_CHANNELORDER_DEINTERLEAVED;
                outBuffer.format.numChannels = 2;
                outBuffer.format.numSamples = AudioConstants.FRAME_SIZE;
                outBuffer.format.sampleRate = AudioConstants.SAMPLE_RATE;
                outBuffer.write();

                LOGGER.info("Output buffer allocated: channels={}, samples={}, data={}",
                    outBuffer.format.numChannels, outBuffer.format.numSamples, outBuffer.data);

                if (outBuffer.data == null || Pointer.nativeValue(outBuffer.data) == 0) {
                    LOGGER.error("CRITICAL: Output buffer data pointer is NULL after iplAudioBufferAllocate!");
                }

                LOGGER.info("Allocated Steam Audio buffers successfully using iplAudioBufferAllocate");

            } catch (Exception e) {
                LOGGER.error("Failed to allocate Steam Audio buffers", e);
            }
        }

        void cleanup() {
                // 鬮ｫ・ｴ闕ｳ讖ｸ・ｽ・ｮ髣鯉ｽｨ繝ｻ・ｽ繝ｻ・､郢晢ｽｻ繝ｻ・ｺ鬯ｨ・ｾ繝ｻ・ｧ驛｢譎｢・ｽ・ｻ驕ｶ莨∬ｱｪ繝ｻ・ｹ隴弱・・ｽ・ｼ隴・搨・ｰ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴弱・・ｽ・ｧ繝ｻ・ｭ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴主・讓溽ｹ晢ｽｻ陝ｶ譎乗凄郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ髯樊ｻゑｽｽ・ｲ郢晢ｽｻ繝ｻ・ｼ髣費｣ｰ繝ｻ・･郢晢ｽｻ繝ｻ・ｮ髴大｣ｼ逕溽ｹ晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩搾ｽｵ繝ｻ・ｺ髮九・竏槭・・ｽ遶丞｣ｹ繝ｻ驛｢譎｢・ｽ・ｻ                // Not needed: IPLAudioBuffer contains counts only; channel layout is implicit

                // Not needed: counts only

                // 鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴弱・・ｽ・ｼ隴∵腸・ｼ諞ｺ縺励・・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ - iplAudioBufferFree鬩幢ｽ｢繝ｻ・ｧ髯ｷ莉｣繝ｻ繝ｻ・ｽ繝ｻ・ｽ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ                            
            if (inBuffer != null) {
                try {
                    SteamAudioLibrary.INSTANCE.iplAudioBufferFree(context, inBuffer.getPointer());
                    LOGGER.debug("Freed input buffer");
                } catch (Exception e) {
                    LOGGER.error("Failed to free input buffer", e);
                }
                inBuffer = null;
            }

            if (outBuffer != null) {
                try {
                    SteamAudioLibrary.INSTANCE.iplAudioBufferFree(context, outBuffer.getPointer());
                    LOGGER.debug("Freed output buffer");
                } catch (Exception e) {
                    LOGGER.error("Failed to free output buffer", e);
                }
                outBuffer = null;
            }

            // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｨ鬩幢ｽ｢隴弱・・ｽ・ｼ隴∫ｵｶ蜃ｾ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓滄Δ譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ
            if (binauralEffect != null) {
                try {
                    PointerByReference effectRef = new PointerByReference(binauralEffect);
                    SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
                    LOGGER.debug("Released binaural effect for player");
                } catch (Exception e) {
                    LOGGER.error("Failed to release binaural effect", e);
                }
                binauralEffect = null;
            }
        }
    }

    /**
     * Steam Audio鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ髯晢ｽｶ隴取得・ｽ・ｹ隰碁托ｽｲ繧会ｽｹ譎｢・ｽ・ｻ
     */
    public synchronized void start() {
        if (running) {
            LOGGER.warn("Steam Audio player is already running");
            return;
        }

        try {
            // Step 1: 鬩幢ｽ｢隴取ｨ費ｽｺ繧会ｽｸ・ｺ郢晢ｽｻ繝ｻ・ｹ隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴弱・ﾂｧ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴弱・ﾂｧ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｭ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ
            if (!NativeLibraryLoader.loadSteamAudio()) {
                LOGGER.error("Failed to load Steam Audio native libraries");
                return;
            }

            // Step 2: Steam Audio Context鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮｣蜴・ｽｽ・ｴ髫ｲ蟶帷樟郢晢ｽｻ
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            contextSettings.logCallback = null;
            contextSettings.allocateCallback = null;
            contextSettings.freeCallback = null;
            contextSettings.write();

            PointerByReference contextRef = new PointerByReference();
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("Failed to create Steam Audio context. Error code: {}", result);
                return;
            }

            context = contextRef.getValue();
            LOGGER.info("Steam Audio context created successfully");

            // Step 3: HRTF鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮｣蜴・ｽｽ・ｴ髫ｲ蟶帷樟郢晢ｽｻ
            // IMPORTANT: frameSize must match the actual audio frame size used (960 samples for Opus 20ms @ 48kHz)
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = AudioConstants.SAMPLE_RATE;
            audioSettings.frameSize = AudioConstants.FRAME_SIZE; // 960 samples
            audioSettings.write();

            LOGGER.info("Steam Audio settings: {}Hz, {} samples/frame", audioSettings.samplingRate, audioSettings.frameSize);

            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            hrtfSettings.sofaFileName = null;
            hrtfSettings.sofaData = null;
            hrtfSettings.sofaDataSize = 0;
            hrtfSettings.write();

            PointerByReference hrtfRef = new PointerByReference();
            result = SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("Failed to create HRTF. Error code: {}", result);
                cleanup();
                return;
            }

            hrtf = hrtfRef.getValue();
            LOGGER.info("Steam Audio HRTF created successfully");

            // Step 4: Java Sound鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｴ・･陝ｶ・ｷ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ蜈ｷ・ｽ・ｻ髫ｴ蠑ｱ繝ｻ繝ｻ繝ｻ蛻ｹ繝ｻ・ｹ驛｢譎｢・ｽ・ｻ
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Audio output format not supported");
                cleanup();
                return;
            }

            outputLine = (SourceDataLine) AudioSystem.getLine(info);
            outputLine.open(audioFormat, AudioConstants.NETWORK_BUFFER_SIZE);
            outputLine.start();

            // Java Sound 陷・ｽｺ陷牙ｸ厥帷ｹｧ・､郢晢ｽｳ邵ｺ・ｮ郢ｧ・ｲ郢ｧ・､郢晢ｽｳ郢ｧ繝ｻ dB闔牙ｩ・ｿ莉｣竏磯坡・ｿ隰ｨ・ｴ
            try {
                if (outputLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gc = (FloatControl) outputLine.getControl(FloatControl.Type.MASTER_GAIN);
                    float target = Math.min(gc.getMaximum(), Math.max(gc.getMinimum(), 0.0f));
                    gc.setValue(target);
                    LOGGER.info("MASTER_GAIN set to {} dB (range: {}..{} dB)", target, gc.getMinimum(), gc.getMaximum());
                } else if (outputLine.isControlSupported(FloatControl.Type.VOLUME)) {
                    FloatControl vc = (FloatControl) outputLine.getControl(FloatControl.Type.VOLUME);
                    float target = 1.0f;
                    vc.setValue(Math.min(1.0f, Math.max(0.0f, target)));
                    LOGGER.info("VOLUME control set to {}", vc.getValue());
                } else {
                    LOGGER.debug("No MASTER_GAIN/VOLUME control available on Java Sound output line");
                }
            } catch (Exception gcEx) {
                LOGGER.warn("Failed to adjust Java Sound output gain control", gcEx);
            }

            running = true;

            // Step 5: 鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｬ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｩ鬯ｯ・ｮ繝ｻ・｢髯ｷ・ｿ繝ｻ・･郢晢ｽｻ繝ｻ・ｧ驛｢譎｢・ｽ・ｻ
            playbackThread = new Thread(this::playbackLoop, "SteamAudio-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            LOGGER.info("Steam Audio player started successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to start Steam Audio player", e);
            cleanup();
        }
    }

    /**
     * Steam Audio鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｨ讓｣繝ｻ鬮ｮ蠑ｱ繝ｻ繝ｻ・ｽ繝ｻ・｢
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        // 鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｬ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｩ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ蜿･・ｸ・ｶ郢ｩ・ｧ郢晢ｽｻ繝ｻ・ｭ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｩ・ｸ繝ｻ・ｽ繝ｻ・ｾ驛｢譎｢・ｽ・ｻ郢晢ｽｻ繝ｻ・ｩ驛｢譎｢・ｽ・ｻ
        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｴ・･陝ｶ・ｷ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ
        if (outputLine != null) {
            outputLine.drain();
            outputLine.stop();
            outputLine.close();
        }

        // 鬮ｯ・ｷ髣鯉ｽｨ繝ｻ・ｽ繝ｻ・ｨ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼSource鬩幢ｽ｢繝ｻ・ｧ髯句ｹ｢・ｽ・ｵ驍ｵ・ｺ鬩｢謳ｾ・ｽ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ
        playerSources.forEach((uuid, source) -> source.cleanup());
        playerSources.clear();

        // Steam Audio鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｽ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ
        cleanup();

        LOGGER.info("Steam Audio player stopped");
    }

    /**
     * Steam Audio鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｽ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｻ
     */
    private void cleanup() {
        if (hrtf != null) {
            try {
                PointerByReference hrtfRef = new PointerByReference(hrtf);
                SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
                LOGGER.info("Released HRTF");
            } catch (Exception e) {
                LOGGER.error("Failed to release HRTF", e);
            }
            hrtf = null;
        }

        if (context != null) {
            try {
                PointerByReference contextRef = new PointerByReference(context);
                SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);
                LOGGER.info("Released Steam Audio context");
            } catch (Exception e) {
                LOGGER.error("Failed to release context", e);
            }
            context = null;
        }
    }

    /**
     * 鬯ｯ・ｮ繝ｻ・ｱ髫ｶ謚ｵ・ｽ・ｭ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｸ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｧ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ繝ｻ蜿門旭繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｿ郢晢ｽｻ繝ｻ・ｽ鬮ｯ・ｷ闔ｨ螟ｲ・ｽ・｣繝ｻ・ｰ驛｢譎｢・ｽ・ｻ髯具ｽｹ繝ｻ・ｻ驍ｵ・ｺ髢ｧ・ｲ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｭ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴主・讓滄Δ譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｭ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｪ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｩ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     * @param samples PCM鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蠑ｱ繝ｻ
     */
    public void addNonPositionalAudio(short[] samples) {
        // TODO: Phase 1鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬯ｯ・ｮ繝ｻ・ｱ髫ｶ謚ｵ・ｽ・ｭ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｸ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｧ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ繝ｻ蜿冶ｾｨ繝ｻ・ｫ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｯ讖ｸ・ｽ・｢郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｫ・ｴ陝ｷ・｢繝ｻ・ｽ繝ｻ・ｪ鬮ｯ讖ｸ・ｽ・ｳ髮九・・ｽ・ｯ郢晢ｽｻ繝ｻ・｣驛｢譎｢・ｽ・ｻ
        // 鬮ｯ貊ゑｽｽ・｢驛｢譎｢・ｽ・ｻ郢晢ｽｻ繝ｻ・ｦ驕ｶ荳橸ｽ｣・ｺ郢晢ｽｻ鬮ｯ貊ゑｽｽ・｢髫ｲ蟶ｷ閻ｸ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬩搾ｽｵ繝ｻ・ｲ驕ｶ荳橸ｽ｣・ｹ隨ｳ譛ｱﾎ碑ｭ趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬮ｯ讖ｸ・ｽ・ｳ髯橸ｽ｢繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｽ鬯ｮ・ｦ繝ｻ・ｪ驍ｵ・ｲ陞ｳ螢ｽﾂ蜻ｵ謔九・・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩搾ｽｵ繝ｻ・ｺ髯ｷ・ｷ繝ｻ・ｶ郢晢ｽｻ驍・私・ｽ・ｮ陋ｹ繝ｻ・ｽ・ｺ繝ｻ・ｯ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｿ郢晢ｽｻ繝ｻ・ｽ鬮ｯ・ｷ闔ｨ螟ｲ・ｽ・｣繝ｻ・ｰ
        LOGGER.debug("Non-positional audio not yet implemented in Steam Audio player");
    }

    /**
     * 鬮｣蜴・ｽｽ・ｴ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ鬩搾ｽｵ繝ｻ・ｺ髯ｷ・ｷ繝ｻ・ｶ郢晢ｽｻ驍・戟ﾂ蠑ｱ繝ｻ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ郢晢ｽｻ鬩幢ｽ｢隴弱・・ｽ・ｺ陋滂ｽ･繝ｻ・･鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬮ｯ・ｷ繝ｻ・ｷ鬯ｮ・ｦ繝ｻ・ｪ郢晢ｽｻ陝ｶ譎乗凄郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ髯樊ｻゑｽｽ・ｲ郢晢ｽｻ繝ｻ・ｼ驛｢譎｢・ｽ・ｻteam Audio鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｫ・ｴ陝ｷ・｢繝ｻ・ｽ繝ｻ・ｪ鬮｣蜴・ｽｽ・ｴ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     */
    public void setPreferredMixerName(String mixerName) {
        // Steam Audio鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯJava Sound API鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ郢晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬮｣蜴・ｽｽ・ｴ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ鬩搾ｽｵ繝ｻ・ｺ髯ｷ・ｷ繝ｻ・ｶ郢晢ｽｻ霑｢證ｦ・ｽ・ｸ繝ｻ・ｺ髮九・竏槭・・ｽ遶擾ｽｫ繝ｻ・ｸ繝ｻ・ｲ驕ｶ謫ｾ・ｽ・ｬ郢晢ｽｻ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ髯橸ｽ｢繝ｻ・ｹ驛｢譎｢・ｽ・ｻ鬮｣蜴・ｽｽ・ｫ髫ｴ蠑ｱ繝ｻ闔諞ｺ縺励・・ｺ髯ｷ莨夲ｽｽ・ｱ驕ｶ莨√・繝ｻ・ｸ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ
        LOGGER.debug("Mixer name setting ignored for Steam Audio (using default Java Sound output)");
    }

    /**
     * 鬩幢ｽ｢隴弱・・ｺ・｢驍ｵ・ｺ陞溘ｑ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｧ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ繝ｻ蜿門旭繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｿ郢晢ｽｻ繝ｻ・ｽ鬮ｯ・ｷ闔ｨ螟ｲ・ｽ・｣繝ｻ・ｰ
     * @param playerId 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼUUID
     * @param samples PCM鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿紋ｺ｢郢晢ｽｻ髯具ｽｹ繝ｻ・ｻ繝ｻ螳亥擠繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     * @param position 3D鬮ｯ貅ｯ・ｶ・｣繝ｻ・ｽ繝ｻ・ｧ鬮ｫ・ｶ髦ｮ蜷ｶ繝ｻ
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position) {
        if (!running || context == null || hrtf == null) {
            return;
        }

        PlayerAudioSource source = playerSources.computeIfAbsent(playerId, id -> {
            try {
                // 鬩幢ｽ｢隴寂・繝ｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｨ鬩幢ｽ｢隴弱・・ｽ・ｼ隴∫ｵｶ蜃ｾ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溽ｹ晢ｽｻ陞ｳ螢ｽ蜑ｲ髫ｲ蟶帷樟郢晢ｽｻ
                SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
                audioSettings.samplingRate = AudioConstants.SAMPLE_RATE;
                audioSettings.frameSize = AudioConstants.FRAME_SIZE;
                audioSettings.write();

                SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
                effectSettings.hrtf = hrtf;
                effectSettings.write();

                PointerByReference effectRef = new PointerByReference();
                int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to create binaural effect for player {}. Error code: {}", id, result);
                    return null;
                }

                LOGGER.debug("Created binaural effect for player {}", id);
                return new PlayerAudioSource(effectRef.getValue());

            } catch (Exception e) {
                LOGGER.error("Failed to create player audio source", e);
                return null;
            }
        });

        if (source != null) {
            source.position = position;
            if (!source.audioQueue.offer(samples)) {
                source.audioQueue.poll(); // 鬮ｯ・ｷ繝ｻ・ｿ郢晢ｽｻ繝ｻ・､鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｬ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・｣繝ｻ・ｰ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｨ謚ｵ・ｽ譛ｱ・ｬ・ｮ繝ｻ・ｯ郢晢ｽｻ繝ｻ・､
                source.audioQueue.offer(samples);
            }
        }
    }

    /**
     * 鬩幢ｽ｢隴弱・・ｺ・｢驍ｵ・ｺ陞溘ｑ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｧ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ繝ｻ蜿門旭繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｿ郢晢ｽｻ繝ｻ・ｽ鬮ｯ・ｷ闔ｨ螟ｲ・ｽ・｣繝ｻ・ｰ驛｢譎｢・ｽ・ｻ髣費｣ｰ繝ｻ・･郢晢ｽｻ繝ｻ・｣郢晢ｽｻ繝ｻ・ｰ鬯ｯ・ｩ繝ｻ・･髣包ｽｳ髣疲ｨ奇ｽｨ謚ｵ・ｽ・ｹ隴主・蜃ｽ繝ｻ蜿門・髢ｼ繧雁ｱ舌・・･驕ｯ・ｶ繝ｻ・ｳ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     * @param playerId 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼUUID
     * @param samples PCM鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿紋ｺ｢郢晢ｽｻ髯具ｽｹ繝ｻ・ｻ繝ｻ螳亥擠繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     * @param position 3D鬮ｯ貅ｯ・ｶ・｣繝ｻ・ｽ繝ｻ・ｧ鬮ｫ・ｶ髦ｮ蜷ｶ繝ｻ
     * @param volumeLevel 鬮ｯ讖ｸ・ｽ・｢郢晢ｽｻ繝ｻ・ｰ鬯ｯ・ｩ繝ｻ・･髣包ｽｳ髣疲ｨ奇ｽｨ謚ｵ・ｽ・ｹ隴主・蜃ｽ繝ｻ蜿紋ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｻhase 1鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｫ・ｴ陝ｷ・｢繝ｻ・ｽ繝ｻ・ｪ鬮｣蜴・ｽｽ・ｴ郢晢ｽｻ繝ｻ・ｿ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position, jp.houlab.mochidsuki.advancedvc.common.VolumeLevel volumeLevel) {
        // Phase 1鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｯ讖ｸ・ｽ・｢郢晢ｽｻ繝ｻ・ｰ鬯ｯ・ｩ繝ｻ・･髣包ｽｳ髣疲ｨ奇ｽｨ謚ｵ・ｽ・ｹ隴主・蜃ｽ繝ｻ蜿門旭繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｴ蜿厄ｽｻ繧托ｽｽ・ｽ繝ｻ・｡鬯ｮ・ｫ陟・侭ﾂｧ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｲ驕ｶ荵怜・繝ｻ・ｸ隰悟･・ｽｽ・ｭ陝ｷ・｢繝ｻ・ｽ繝ｻ・ｬ鬯ｨ・ｾ繝ｻ・ｧ驛｢譎｢・ｽ・ｻ驕ｶ莨√・繝ｻ・ｹ隴寂・繝ｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ
        addPositionalAudio(playerId, samples, position);
    }

    /**
     * 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴主・讓溘・蜿悶渚繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・｣繝ｻ・ｰ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｨ謚ｵ・ｽ譛ｱ・ｬ・ｮ繝ｻ・ｯ郢晢ｽｻ繝ｻ・､
     */
    public void removePlayerStream(UUID playerId) {
        PlayerAudioSource source = playerSources.remove(playerId);
        if (source != null) {
            source.cleanup();
            LOGGER.debug("Removed player audio source: {}", playerId);
        }
    }

    /**
     * 鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ
     */
    private void playbackLoop() {
        while (running) {
            try {
                // Initialize mix buffer for this frame
                int[] mixBuffer = new int[AudioConstants.FRAME_SIZE * 2];

                // 鬮ｯ・ｷ繝ｻ・ｷ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｬ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｮ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｯ讖ｸ・ｽ・｢郢晢ｽｻ繝ｻ・ｰ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｧ・ｭ郢晢ｽｻ鬯ｨ・ｾ郢晢ｽｻ郢晢ｽｻ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬩幢ｽ｢隴弱・・ｽ・ｺ陋滂ｽ･繝ｻ・･鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ
                mixPositionalAudio(mixBuffer);

                // int[] 鬩包ｽｶ驗呻ｽｫ郢晢ｽｻshort[] 鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､鬩搾ｽｵ繝ｻ・ｺ髯ｷ莨夲ｽｽ・ｱ驕ｯ・ｶ繝ｻ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｴ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ
                short[] outputSamples = new short[AudioConstants.FRAME_SIZE * 2];
                for (int i = 0; i < AudioConstants.FRAME_SIZE * 2; i++) {
                    int sample = mixBuffer[i];
                    if (outputGain != 1.0) {
                        sample = (int) Math.round(sample * outputGain);
                    }
                    // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驛｢譎｢・ｽ・ｴ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ
                    if (sample > Short.MAX_VALUE) {
                        sample = Short.MAX_VALUE;
                    } else if (sample < Short.MIN_VALUE) {
                        sample = Short.MIN_VALUE;
                    }
                    outputSamples[i] = (short) sample;
                }

                // byte鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､鬩搾ｽｵ繝ｻ・ｺ髯ｷ莨夲ｽｽ・ｱ驕ｯ・ｶ繝ｻ・ｻ鬮ｯ・ｷ・つ髯ｷ・･繝ｻ・ｲ髯ｷ繝ｻ・ｽ・ｽ
                byte[] outputBytes = shortsToBytes(outputSamples);
                outputLine.write(outputBytes, 0, outputBytes.length);

            } catch (Exception e) {
                if (running) {
                    LOGGER.error("Error in playback loop", e);
                }
            }
        }
    }

    /**
     * 鬩幢ｽ｢隴弱・・ｺ・｢驍ｵ・ｺ陞溘ｑ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｧ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ繝ｻ蜿門旭繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ髫ｲ・｡郢晢ｽｻeam Audio鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｦ鬯ｨ・ｾ郢晢ｽｻ郢晢ｽｻ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬩幢ｽ｢隴弱・・ｽ・ｺ陋滂ｽ･繝ｻ・･鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ
     */
    private void mixPositionalAudio(int[] mixBuffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 listenerPos = mc.player.position().add(0, 1.5, 0); // 鬯ｮ・｢繝ｻ・ｰ郢晢ｽｻ繝ｻ・ｳ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｯ繝ｻ・ｮ髯区ｻゑｽｽ・･郢晢ｽｻ郢晢ｽｻ
        float listenerYaw = mc.player.getYRot(); // 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ・ｷ繝ｻ・ｷ髣比ｼ夲ｽｽ・｣驕ｯ・ｶ繝ｻ・ｳ驛｢譎｢・ｽ・ｻ髣費｣ｰ繝ｻ・･郢晢ｽｻ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬮ｫ・ｰ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｰ鬮ｮ荳ｻ萓幄ｭｯ竏壹・繝ｻ・ｼ驛｢譎｢・ｽ・ｻ
        float listenerPitch = mc.player.getXRot(); // 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮｣蜴・ｽｽ・ｫ郢晢ｽｻ繝ｻ・ｯ鬯ｮ・ｫ驍・ｽｲ隲ｷ・｣郢晢ｽｻ繝ｻ・ｼ髣費｣ｰ繝ｻ・･郢晢ｽｻ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｦ鬮ｫ・ｰ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｰ鬮ｮ荳ｻ萓幄ｭｯ竏壹・繝ｻ・ｼ驛｢譎｢・ｽ・ｻ

        playerSources.forEach((playerId, source) -> {
            short[] samples = source.audioQueue.poll();
            if (samples == null || source.position == null || source.binauralEffect == null) {
                return;
            }

            if (source.inBuffer == null || source.outBuffer == null) {
                LOGGER.warn("Steam Audio buffers not allocated for player {}", playerId);
                return;
            }

            LOGGER.debug("Processing audio for player {}: {} samples", playerId, samples.length);

            try {
                // === Step 1: 鬯ｯ・ｮ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｮ荵昴・郢晢ｽｻ髯敖繝ｻ・ｿ鬮ｯ・ｷ繝ｻ・ｷ髣比ｼ夲ｽｽ・｣驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溘・蜿門旭繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｮ・ｫ繝ｻ・ｪ鬮｢・ｧ繝ｻ・ｲ郢晢ｽｻ繝ｻ・ｮ驛｢譎｢・ｽ・ｻ===
                Vec3 sourcePos = source.position.add(0, 1.5, 0); // 鬯ｯ・ｮ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｮ荵昴・郢晢ｽｻ驛｢譎｢・ｽ・ｻ鬮ｯ・ｷ繝ｻ・ｿ郢晢ｽｻ繝ｻ・｣鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｯ繝ｻ・ｮ髯区ｻゑｽｽ・･郢晢ｽｻ郢晢ｽｻ
                Vec3 direction = sourcePos.subtract(listenerPos).normalize();

                // 鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿厄ｽｨ謚ｵ・ｽ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ・ｷ繝ｻ・ｷ髣比ｼ夲ｽｽ・｣驕ｯ・ｶ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陜｣莉ｰﾂ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ髯ｷ莨夲ｽｽ・ｱ髫ｨ・ｳ郢晢ｽｻ・つ繝ｻ・ｶ郢晢ｽｻ繝ｻ・ｸ鬮ｯ譏ｴ繝ｻ繝ｻ・ｽ繝ｻ・ｾ鬮ｫ・ｴ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｹ鬮ｯ・ｷ繝ｻ・ｷ髣比ｼ夲ｽｽ・｣驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溘・蠑ｱ繝ｻ
                double yawRad = Math.toRadians(listenerYaw);
                double pitchRad = Math.toRadians(listenerPitch);

                // 鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ・ｷ鬯伜ｾ鯉ｼ企劑ﾂ繝ｻ・ｿ鬩幢ｽ｢隴主・蜃ｽ驍ｵ・ｺ鬩｢謳ｾ・ｽ・ｹ隴主・讓溘・蠑ｱ繝ｻ
                double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
                double forwardY = -Math.sin(pitchRad);
                double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

                // 鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ・ｷ繝ｻ・ｿ郢晢ｽｻ繝ｻ・ｳ鬩幢ｽ｢隴主・蜃ｽ驍ｵ・ｺ鬩｢謳ｾ・ｽ・ｹ隴主・讓溘・蠑ｱ繝ｻ
                double rightX = Math.cos(yawRad);
                double rightZ = Math.sin(yawRad);

                // 鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮｣蛹・ｽｽ・ｳ鬩怜遜・ｽ・ｫ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溘・蠑ｱ繝ｻ
                double upX = -Math.sin(yawRad) * Math.sin(pitchRad);
                double upY = Math.cos(pitchRad);
                double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

                // 鬯ｯ・ｮ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｳ鬮ｮ荵昴・郢晢ｽｻ髯敖繝ｻ・ｿ鬮ｯ・ｷ繝ｻ・ｷ髣比ｼ夲ｽｽ・｣驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溘・蜿門旭繝ｻ・ｹ繝ｻ・ｧ髯句ｹ｢・ｽ・ｵ繝ｻ蜿悶渚繝ｻ・ｹ繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴惹ｼ夲ｽｽ・ｿ繝ｻ・ｫ驛｢譎｢・ｽ・ｻ鬮ｯ貅ｯ・ｶ・｣繝ｻ・ｽ繝ｻ・ｧ鬮ｫ・ｶ霓｣蛟ｩ・｡・ｷ郢晢ｽｻ繝ｻ・ｳ郢晢ｽｻ繝ｻ・ｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､
                float relativeX = (float) (direction.x * rightX + direction.z * rightZ);
                float relativeY = (float) (direction.x * upX + direction.y * upY + direction.z * upZ);
                float relativeZ = (float) (direction.x * forwardX + direction.y * forwardY + direction.z * forwardZ);

                // 鬮ｯ・ｷ鬮ｮ繝ｻﾂ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｽ鬯ｮ・ｦ繝ｻ・ｪ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溘・蜿門旭繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｮ蠑ｱ繝ｻ繝ｻ・ｽ繝ｻ・｣鬯ｮ・ｫ驕ｨ繧托ｽｽ・ｸ隶抵ｽｫ陝・・・ｹ譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻteam Audio API鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｮ・ｫ髯ｬ諛医・郢晢ｽｻ繝ｻ・ｻ郢晢ｽｻ繝ｻ・ｶ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                float length = (float) Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);
                if (length > 0.0001f) {  // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｭ鬯ｯ・ｮ繝ｻ・ｯ郢晢ｽｻ繝ｻ・､鬯ｩ髦ｪ・・濤・ｲ郢晢ｽｻ陝ｶ譎｢・ｽ・ｫ繝ｻ・ｦ郢晢ｽｻ繝ｻ・ｲ鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ
                    relativeX /= length;
                    relativeY /= length;
                    relativeZ /= length;
                } else {
                    // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｭ鬩幢ｽ｢隴主・蜃ｽ驍ｵ・ｺ鬩｢謳ｾ・ｽ・ｹ隴主・讓溘・蜿門旭繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｯ諛ｶ・ｽ・｣郢晢ｽｻ繝ｻ・ｴ鬮ｯ・ｷ繝ｻ・ｷ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｻ鬮ｯ・ｷ鬯伜ｾ鯉ｼ企劑ﾂ繝ｻ・ｿ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｨ・｣繝ｻ・ｫ郢晢ｽｻ繝ｻ・ｸ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ驕ｯ・ｶ繝ｻ・ｻ鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ郢晢ｽｻ霑｢證ｦ・ｽ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｨ鬩搾ｽｵ繝ｻ・ｺ髯ｷ・ｷ繝ｻ・ｶ郢晢ｽｻ郢晢ｽｻ
                    relativeX = 0.0f;
                    relativeY = 0.0f;
                    relativeZ = 1.0f;
                }

                // === Step 2: short[] 鬩包ｽｶ驗呻ｽｫ郢晢ｽｻfloat[] 鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､ ===
                // 鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｵ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿門・繝ｻ・ｬ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｩ蠅捺・繝ｻ・ｽ繝ｻ・ｺ鬯ｮ・ｫ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ
                if (samples.length != AudioConstants.FRAME_SIZE) {
                    LOGGER.warn("Sample size mismatch: expected {}, got {}. Skipping.", AudioConstants.FRAME_SIZE, samples.length);
                    return;
                }

                // 鬮ｯ・ｷ髣鯉ｽｨ繝ｻ・ｽ繝ｻ・･鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ繝ｻ・ｰ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴惹ｸ橸ｽｹ・ｲ繝ｻ蜿門旭繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｫ・ｴ陝・｢・つ鬮ｯ蜈ｷ・ｽ・ｻ髫ｴ謫ｾ・ｽ・ｴ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ郢晢ｽｻ繝ｻ・･鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・､鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ陜｣・､繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｭ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｹ繝ｻ・｢郢晢ｽｻ繝ｻ・ｼ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｧ鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                if (LOGGER.isDebugEnabled() && samples.length > 5) {
                    LOGGER.debug("Input samples[0-5] (short): {}, {}, {}, {}, {}, {}",
                        samples[0], samples[1], samples[2], samples[3], samples[4], samples[5]);
                }

                float[] inputSamples = new float[AudioConstants.FRAME_SIZE];
                for (int i = 0; i < AudioConstants.FRAME_SIZE; i++) {
                    inputSamples[i] = samples[i] / 32768.0f; // short 鬩包ｽｶ驗呻ｽｫ郢晢ｽｻfloat (-1.0 驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ+1.0)
                }

                // float鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､鬮ｯ貅ｷ萓帙・・ｾ陟募ｾ後・鬮ｫ・ｴ陝・｢・つ鬮ｯ蜈ｷ・ｽ・ｻ髫ｴ謫ｾ・ｽ・ｴ驛｢譎｢・ｽ・ｻ鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ郢晢ｽｻ繝ｻ・･鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・､鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ陜｣・､繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｭ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｹ繝ｻ・｢郢晢ｽｻ繝ｻ・ｼ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｧ鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                if (LOGGER.isDebugEnabled() && inputSamples.length > 5) {
                    LOGGER.debug("Input samples[0-5] (float): {}, {}, {}, {}, {}, {}",
                        inputSamples[0], inputSamples[1], inputSamples[2], inputSamples[3], inputSamples[4], inputSamples[5]);
                }

                LOGGER.debug("Direction: ({}, {}, {})", relativeX, relativeY, relativeZ);

                // === Step 3: Steam Audio鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴弱・・ｽ・ｼ隴∵腸・ｼ諞ｺ縺励・・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｫ・ｴ陷ｴ繝ｻ・ｽ・ｽ繝ｻ・ｸ鬩搾ｽｵ繝ｻ・ｺ髯晢｣ｰ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｾ郢晢ｽｻ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ驛｢譎｢・ｽ・ｻ鬮｢・ｧ繝ｻ・ｲ髯晢ｽｲ繝ｻ・ｩ鬮ｫ・ｰ隴会ｽｦ繝ｻ・ｽ繝ｻ・･鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｡鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ===
                // inBuffer.data鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯfloat**驛｢譎｢・ｽ・ｻ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                source.inBuffer.read();
                // Ensure buffer format is set explicitly
                source.inBuffer.format.channelLayoutType = SteamAudioLibrary.IPLChannelLayoutType.IPL_CHANNELLAYOUTTYPE_SPEAKERS;
                source.inBuffer.format.channelOrder = SteamAudioLibrary.IPLChannelOrder.IPL_CHANNELORDER_DEINTERLEAVED;
                source.inBuffer.format.numChannels = 1;
                source.inBuffer.format.numSamples = AudioConstants.FRAME_SIZE;
                source.inBuffer.format.sampleRate = AudioConstants.SAMPLE_RATE;
                source.inBuffer.write(); // 鬮ｫ・ｴ陝・｢・つ鬮ｫ・ｴ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｷ・ｶ・趣ｽｨ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｣鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴取得・ｽ・ｳ繝ｻ・ｨ郢晢ｽｻ陝ｶ譎乗套郢晢ｽｻ繝ｻ・ｭ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬯ｮ・ｴ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ

                // data鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ髢ｾ・･繝ｻ・ｹ隴擾ｽｶ郢晢ｽｻ繝ｻ蜿悶・繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴取ｨ費ｽｺ繧托ｽｾ蜿門旭繝ｻ・ｹ隴弱・・ｺ・｢驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｧ・ｫ陟募ｮ｣霎ｧ髴域鱒繝ｻ
                Pointer[] inChannelPointers = source.inBuffer.data.getPointerArray(0, source.inBuffer.format.numChannels);
                if (inChannelPointers == null || inChannelPointers.length < 1) {
                    LOGGER.error("Failed to get input channel pointers! channels={}", source.inBuffer.format.numChannels);
                    return;
                }

                Pointer channel0Ptr = inChannelPointers[0];
                if (channel0Ptr == null) {
                    LOGGER.error("Input channel 0 pointer is null!");
                    return;
                }

                // float鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩幢ｽ｢繝ｻ・ｧ髯句ｹ｢・ｽ・ｵ驛｢譎｢・ｽ・ｭ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴擾ｽｴ郢晢ｽｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴弱・ﾂｧ繝ｻ譛ｱ雎ｪ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｫ・ｴ陷ｴ繝ｻ・ｽ・ｽ繝ｻ・ｸ鬩搾ｽｵ繝ｻ・ｺ髯晢｣ｰ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｾ郢晢ｽｻ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ
                channel0Ptr.write(0, inputSamples, 0, inputSamples.length);

                // 鬮ｫ・ｴ陷ｴ繝ｻ・ｽ・ｽ繝ｻ・ｸ鬩搾ｽｵ繝ｻ・ｺ髯晢｣ｰ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｾ郢晢ｽｻ繝ｻ・ｼ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬯ｩ蠅捺・繝ｻ・ｽ繝ｻ・ｺ鬯ｮ・ｫ繝ｻ・ｱ髫ｰ・ｳ繝ｻ・ｾ郢晢ｽｻ繝ｻ・ｼ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｧ鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                float[] verifyWrite = channel0Ptr.getFloatArray(0, 6);
                LOGGER.debug("Wrote {} float samples directly to input buffer channel 0", inputSamples.length);
                LOGGER.debug("Verification - buffer[0-5]: {}, {}, {}, {}, {}, {}",
                    verifyWrite[0], verifyWrite[1], verifyWrite[2], verifyWrite[3], verifyWrite[4], verifyWrite[5]);

                // === Step 4: 鬩幢ｽ｢隴寂・繝ｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｨ鬩幢ｽ｢隴弱・・ｽ・ｼ隴∫ｵｶ蜃ｾ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓滄Δ譎｢・ｽ・ｱ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｡鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬯ｮ・ｫ繝ｻ・ｪ郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ驛｢譎｢・ｽ・ｻ===
                SteamAudioLibrary.IPLBinauralEffectParams params = new SteamAudioLibrary.IPLBinauralEffectParams();

                // direction鬩幢ｽ｢隴弱・・ｽ・ｼ隴∫ｵｶ隘夜ｩ幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴取得・ｽ・ｳ繝ｻ・ｨ驕ｶ莨・ｽｦ・ｴ・つ繝ｻ・ｶ郢晢ｽｻ繝ｻ・ｴ鬮ｫ・ｰ隴会ｽｦ繝ｻ・ｽ繝ｻ・･鬮ｯ蛹ｺ・ｻ繧托ｽｽ・ｽ繝ｻ・､鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ髯樊ｻゑｽｽ・ｲ郢晢ｽｻ繝ｻ・ｼ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｭ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ鬩幢ｽ｢隴主・讓溽ｹ晢ｽｻ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ髮倶ｼ∝ｱｮ繝ｻ・ｽ繝ｻ・ｧ鬩墓得・ｽ・ｩ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬯ｮ・ｮ繝ｻ・｣郢晢ｽｻ繝ｻ・ｼ驛｢譎｢・ｽ・ｻ
                params.direction.x = relativeX;
                params.direction.y = relativeY;
                params.direction.z = relativeZ;

                params.interpolation = 1; // IPL_HRTFINTERPOLATION_BILINEAR (Linear interpolation)
                params.spatialBlend = 1.0f; // Full 3D spatialization
                params.hrtf = hrtf; // Use global HRTF
                params.peakDelays = null; // Not using peak delays
                params.write(); // 鬩幢ｽ｢隴乗・・ｽ・ｻ繝ｻ・｣繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・｡鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ髴難ｽ｣陋滂ｽ･郢晢ｽｻ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｻ・ｻ繝ｻ・ｽ陞ｳ螟ｲ・ｽ・ｭ陷ｴ繝ｻ・ｽ・ｽ繝ｻ・ｸ鬩搾ｽｵ繝ｻ・ｺ髯晢｣ｰ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｾ郢晢ｽｻ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ

                LOGGER.debug("Params - hrtf: {}, spatialBlend: {}, interpolation: {}, direction: ({}, {}, {})",
                    params.hrtf, params.spatialBlend, params.interpolation, params.direction.x, params.direction.y, params.direction.z);

                // === Step 5: 鬩幢ｽ｢隴寂・繝ｻ驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴取得・ｽ・ｼ繝ｻ・ｱ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｩ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｨ鬩幢ｽ｢隴弱・・ｽ・ｼ隴∫ｵｶ蜃ｾ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢隴主・讓溽ｹ晢ｽｻ陝ｶ譎｢・ｽ・ｩ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｩ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ ===
                LOGGER.debug("About to call iplBinauralEffectApply:");
                LOGGER.debug("  binauralEffect: {}", source.binauralEffect);
                LOGGER.debug("  inBuffer: {}", source.inBuffer.getPointer());
                LOGGER.debug("  outBuffer: {}", source.outBuffer.getPointer());
                LOGGER.debug("  direction: ({}, {}, {})", params.direction.x, params.direction.y, params.direction.z);

                int result;
                try {
                    LOGGER.debug("Calling iplBinauralEffectApply NOW...");
                    result = SteamAudioLibrary.INSTANCE.iplBinauralEffectApply(
                            source.binauralEffect,
                            params.getPointer(),  // 鬩幢ｽ｢隴乗・・ｽ・ｻ繝ｻ・｣繝ｻ荳ｻ・ｸ・ｷ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・｡鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｷ・ｶ郢晢ｽｻ鬩幢ｽ｢隴弱・・ｺ・｢驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩幢ｽ｢繝ｻ・ｧ髯ｷ・ｻ髣鯉ｽｨ繝ｻ・ｽ繝ｻ・ｸ郢晢ｽｻ繝ｻ・｡鬩搾ｽｵ繝ｻ・ｺ驛｢譎｢・ｽ・ｻ
                            source.inBuffer.getPointer(),
                            source.outBuffer.getPointer()
                    );
                    LOGGER.debug("iplBinauralEffectApply returned: {}", result);
                } catch (Exception e) {
                    LOGGER.error("Exception during iplBinauralEffectApply for player {}", playerId, e);
                    return;
                } catch (Error e) {
                    LOGGER.error("Error during iplBinauralEffectApply for player {}", playerId, e);
                    return;
                }

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to apply binaural effect for player {}. Error code: {}", playerId, result);
                    return;
                }

                LOGGER.debug("Binaural effect applied successfully for player {}", playerId);

                // === Step 6: Steam Audio鬩幢ｽ｢隴寂・繝ｻ驛｢譎｢・ｽ・｣鬩幢ｽ｢隴弱・・ｽ・ｼ隴∵腸・ｼ諞ｺ縺励・・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ髯具ｽｾ陜ｮ・｡郢晢ｽｻ繝ｻ・ｭ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬮ｯ・ｷ繝ｻ・ｿ髫ｰ雋ｻ・ｽ・ｶ郢晢ｽｻ驗呻ｽｫ郢晢ｽｻ鬮｢・ｧ繝ｻ・ｲ髯晢ｽｲ繝ｻ・ｩ鬮ｫ・ｰ隴会ｽｦ繝ｻ・ｽ繝ｻ・･鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｡鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・｢鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｪ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｢鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｯ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｹ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ===
                // outBuffer.data鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯfloat**驛｢譎｢・ｽ・ｻ髯具ｽｹ繝ｻ・ｻ驛｢譎｢・ｽ・ｻ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
                source.outBuffer.read();
                source.outBuffer.format.channelLayoutType = SteamAudioLibrary.IPLChannelLayoutType.IPL_CHANNELLAYOUTTYPE_SPEAKERS;
                source.outBuffer.format.channelOrder = SteamAudioLibrary.IPLChannelOrder.IPL_CHANNELORDER_DEINTERLEAVED;
                source.outBuffer.format.numChannels = 2;
                source.outBuffer.format.numSamples = AudioConstants.FRAME_SIZE;
                source.outBuffer.format.sampleRate = AudioConstants.SAMPLE_RATE;
                source.outBuffer.write(); // 鬮ｫ・ｴ陝・｢・つ鬮ｫ・ｴ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｰ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬮ｫ・ｶ陜｣・､雎｢・ｸ繝ｻ縺､ﾂ郢晢ｽｻ繝ｻ・ｰ鬮｣蜴・ｽｽ・ｴ鬮ｦ・ｮ陷ｷ・ｶ・趣ｽｨ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・｣鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｫ鬩幢ｽ｢隴取得・ｽ・ｳ繝ｻ・ｨ郢晢ｽｻ陝ｶ譎乗套郢晢ｽｻ繝ｻ・ｭ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｿ鬯ｮ・ｴ髮懶ｽ｣繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ繝ｻ縺､ﾂ

                // data鬩搾ｽｵ繝ｻ・ｺ髣包ｽｵ隴趣ｽ｢繝ｻ・ｽ髢ｾ・･繝ｻ・ｹ隴擾ｽｶ郢晢ｽｻ繝ｻ蜿悶・繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢隴取ｨ費ｽｺ繧托ｽｾ蜿門旭繝ｻ・ｹ隴弱・・ｺ・｢驍ｵ・ｺ郢晢ｽｻ繝ｻ・ｹ隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｿ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩幢ｽ｢繝ｻ・ｧ鬮ｮ蛹ｺ・ｧ・ｫ陟募ｮ｣霎ｧ髴域鱒繝ｻ
                // 髯橸ｽｳ霑壼生繝ｻ驍ｵ・ｺ繝ｻ・ｪ髯ｷ・ｿ隰費ｽｶ繝ｻ鬘俶弱・・ｺ驍ｵ・ｺ郢晢ｽｻ interleave API驍ｵ・ｺ繝ｻ・ｧL,R,L,R...驛｢・ｧ髮区ｧｫ蠕宣辧霈斐・
                float[] outputSamples = new float[AudioConstants.FRAME_SIZE * 2];
                try {
                    SteamAudioLibrary.INSTANCE.iplAudioBufferInterleave(
                            context,
                            source.outBuffer.getPointer(),
                            outputSamples
                    );
                } catch (Throwable t) {
                    LOGGER.error("Failed to interleave Steam Audio output buffer", t);
                    return;
                }
                LOGGER.debug("Read and interleaved {} stereo samples from output buffer", AudioConstants.FRAME_SIZE);
                if (LOGGER.isDebugEnabled() && outputSamples.length > 10) {
                    LOGGER.debug("Output samples[0-5]: {}, {}, {}, {}, {}, {}",
                        outputSamples[0], outputSamples[1], outputSamples[2],
                        outputSamples[3], outputSamples[4], outputSamples[5]);
                }

                // === Step 7: float[] 鬩包ｽｶ驗呻ｽｫ郢晢ｽｻint[] 鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩幢ｽ｢隴弱・・ｽ・ｺ陋滂ｽ･繝ｻ・･鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｷ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｰ ===
                int nonZeroCount = 0;
                for (int i = 0; i < outputSamples.length; i++) {
                    if (i < mixBuffer.length) {
                        // Steam Audio鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｮ・つ繝ｻ・ｲ鬮ｯ譏ｴ繝ｻ繝ｻ・ｸ陞ゅ・・ｽ・ｼ郢晢ｽｻ繝ｻ・ｹ繝ｻ・ｧ驕ｶ荳橸ｽ｣・ｺ郢晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｮ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬯ｮ・ｯ繝ｻ・ｬ髫ｲ蟷・か繝ｻ・ｽ繝ｻ・ｭ郢晢ｽｻ繝ｻ・｣鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｲ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ髯句ｹ｢・ｽ・ｵ郢晢ｽｻ郢晢ｽｻ繝ｻ・ｸ繝ｻ・ｺ鬮ｦ・ｮ陷ｷ・ｶ・つ陝ｶ譎｢・ｽ・ｩ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｩ鬯ｨ・ｾ陋ｹ繝ｻ・ｽ・ｽ繝ｻ・ｨ
                        int sample = (int) (outputSamples[i] * 32768.0f * BINAURAL_COMPENSATION_GAIN);
                        mixBuffer[i] += sample;
                        if (sample != 0) nonZeroCount++;
                    }
                }

                if (nonZeroCount > 0) {
                    LOGGER.debug("Mixed {} non-zero samples into buffer", nonZeroCount);
                } else {
                    LOGGER.warn("All output samples are zero!");
                }

            } catch (Exception e) {
                LOGGER.error("Error processing positional audio for player {}", playerId, e);
            }
        });
    }

    /**
     * short鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩幢ｽ｢繝ｻ・ｧ鬯ｪ・ｰ陝・ｽｳte鬯ｯ・ｩ雋企屮・ｽ・ｦ驗呻ｽｫ郢晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬮ｯ讓奇ｽｺ・ｽ陋ｻ・､鬯ｩ・ｪ繝ｻ・､
     */
    private byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .put(shorts);
        return bytes;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 鬮ｯ・ｷ郢晢ｽｻ繝ｻ・ｽ繝ｻ・ｺ鬮ｯ・ｷ霑壼遜・ｽ・ｸ陷ｷ・ｶ繝ｻ繝ｻﾎ斐・・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ髯橸ｽｳ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｨ郢晢ｽｻ繝ｻ・ｭ鬮ｯ讖ｸ・ｽ・ｳ髯樊ｻゑｽｽ・ｲ郢晢ｽｻ繝ｻ・ｼ驛｢譎｢・ｽ・ｻ.0驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ.0鬩搾ｽｵ繝ｻ・ｲ驕ｶ謫ｾ・ｽ・ｵ髫ｲ・､陷ｻ・ｵ隴ｽ譁舌・繝ｻ・ｧ400%驛｢譎｢・ｽ・ｻ驛｢譎｢・ｽ・ｻ
     */
    public void setOutputGain(double gain) {
        // Steam Audio鬯ｩ謳ｾ・ｽ・ｨ髴托ｽｹ陞滂ｽｲ繝ｻ・ｽ繝ｻ・ｷ郢晢ｽｻ繝ｻ・ｯ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｧ鬩搾ｽｵ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｯ鬯ｮ・ｯ繝ｻ・ｬ髫ｲ蟷・か繝ｻ・ｽ繝ｻ・ｭ郢晢ｽｻ繝ｻ・｣鬮ｯ貅ｷ萓帙・・ｾ陞ｽ・ｯ郢晢ｽｻ鬩搾ｽｵ繝ｻ・ｺ鬮ｴ驛・ｽｲ・ｻ繝ｻ・ｽ髢ｾ・･繝ｻ・ｸ繝ｻ・ｺ郢晢ｽｻ繝ｻ・ｫ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｦ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｶ鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｼ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・ｲ鬩幢ｽ｢繝ｻ・ｧ郢晢ｽｻ繝ｻ・､鬩幢ｽ｢隴趣ｽ｢繝ｻ・ｽ繝ｻ・ｳ鬩幢ｽ｢繝ｻ・ｧ髯ｷ・ｻ闔・･繝ｻ・ｯ繝ｻ・ｺ鬩搾ｽｵ繝ｻ・ｺ髣比ｼ夲ｽｽ・｣郢晢ｽｻ霑｢證ｦ・ｽ・ｸ繝ｻ・ｺ髮九・竏槭・・ｽ遶擾ｽｫ繝ｻ・ｸ繝ｻ・ｲ驕ｶ謫ｾ・ｽ・ｽ郢晢ｽｻ繝ｻ・ｸ髣費ｽｨ遶乗剌・ｿ諞ｺﾎ斐・・ｧ鬮ｮ蛹ｺ・ｩ・ｸ繝ｻ・ｽ繝ｻ・ｰ髣比ｼ夲ｽｽ・｣郢晢ｽｻ繝ｻ・ｰ鬮ｯ貅ｷ・ｼ・ｱ郢晢ｽｻ郢晢ｽｻ繝ｻ・｡鬩幢ｽ｢繝ｻ・ｧ驛｢譎｢・ｽ・ｻ        this.outputGain = Math.max(0.0, Math.min(8.0, gain));
        LOGGER.info("Output gain set to: {}", this.outputGain);
    }
}

