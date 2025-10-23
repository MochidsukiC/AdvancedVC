package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import org.concentus.*;
import org.slf4j.Logger;

/**
 * Opusコーデックのラッパークラス
 * エンコード（PCM → Opus）とデコード（Opus → PCM）を処理
 */
public class OpusCodec {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Opusエンコーダー
     */
    public static class Encoder {
        private final OpusEncoder encoder;
        private final int bitrate;
        private final int complexity;

        public Encoder(int bitrate, int complexity) throws OpusException {
            this.encoder = new OpusEncoder(
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNELS,
                    OpusApplication.OPUS_APPLICATION_VOIP
            );
            this.bitrate = bitrate;
            this.complexity = complexity;

            // エンコーダー設定
            encoder.setBitrate(bitrate);
            encoder.setComplexity(complexity);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);

            LOGGER.info("Opus Encoder initialized: bitrate={}, complexity={}", bitrate, complexity);
        }

        /**
         * PCM音声をOpusにエンコード
         * @param pcmData PCMサンプル（short[]）
         * @return エンコードされたOpusデータ
         */
        public byte[] encode(short[] pcmData) {
            try {
                byte[] output = new byte[AudioConstants.MAX_PACKET_SIZE];
                int encodedLength = encoder.encode(pcmData, 0, AudioConstants.FRAME_SIZE, output, 0, output.length);

                // 実際のサイズに切り詰め
                byte[] result = new byte[encodedLength];
                System.arraycopy(output, 0, result, 0, encodedLength);
                return result;
            } catch (OpusException e) {
                LOGGER.error("Failed to encode audio", e);
                return new byte[0];
            }
        }

        public void setBitrate(int bitrate) {
            try {
                encoder.setBitrate(bitrate);
            } catch (OpusException e) {
                LOGGER.error("Failed to set bitrate", e);
            }
        }
    }

    /**
     * Opusデコーダー
     */
    public static class Decoder {
        private final OpusDecoder decoder;

        public Decoder() throws OpusException {
            this.decoder = new OpusDecoder(AudioConstants.SAMPLE_RATE, AudioConstants.CHANNELS);
            LOGGER.info("Opus Decoder initialized");
        }

        /**
         * Opusデータをデコード
         * @param opusData エンコードされたOpusデータ
         * @return デコードされたPCMサンプル
         */
        public short[] decode(byte[] opusData) {
            try {
                short[] output = new short[AudioConstants.FRAME_SIZE];
                int decodedSamples = decoder.decode(opusData, 0, opusData.length, output, 0, AudioConstants.FRAME_SIZE, false);

                // 実際のサイズに切り詰め（通常はFRAME_SIZEと一致するはず）
                if (decodedSamples != AudioConstants.FRAME_SIZE) {
                    short[] result = new short[decodedSamples];
                    System.arraycopy(output, 0, result, 0, decodedSamples);
                    return result;
                }
                return output;
            } catch (OpusException e) {
                LOGGER.error("Failed to decode audio", e);
                return new short[0];
            }
        }

        /**
         * パケットロス時の処理（FEC: Forward Error Correction）
         * @return 補完されたPCMサンプル
         */
        public short[] decodeLost() {
            try {
                short[] output = new short[AudioConstants.FRAME_SIZE];
                decoder.decode(null, 0, 0, output, 0, AudioConstants.FRAME_SIZE, false);
                return output;
            } catch (OpusException e) {
                LOGGER.error("Failed to decode lost packet", e);
                return new short[AudioConstants.FRAME_SIZE];
            }
        }
    }

    /**
     * 高品質エンコーダーを作成（シミュレーション/バンドモード用）
     */
    public static Encoder createHighQualityEncoder() {
        try {
            return new Encoder(AudioConstants.OPUS_BITRATE_HIGH, AudioConstants.OPUS_COMPLEXITY_HIGH);
        } catch (OpusException e) {
            LOGGER.error("Failed to create high quality encoder", e);
            return null;
        }
    }

    /**
     * 低品質エンコーダーを作成（デバイスモード用）
     */
    public static Encoder createLowQualityEncoder() {
        try {
            return new Encoder(AudioConstants.OPUS_BITRATE_LOW, AudioConstants.OPUS_COMPLEXITY_LOW);
        } catch (OpusException e) {
            LOGGER.error("Failed to create low quality encoder", e);
            return null;
        }
    }

    /**
     * デコーダーを作成
     */
    public static Decoder createDecoder() {
        try {
            return new Decoder();
        } catch (OpusException e) {
            LOGGER.error("Failed to create decoder", e);
            return null;
        }
    }
}
