package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.mojang.logging.LogUtils;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.slf4j.Logger;

/**
 * Steam Audio IPLAudioBuffer の手動メモリ管理実装
 *
 * ARM64環境でのfloat**（ポインタのポインタ）問題を解決するため、
 * JNA Structureではなく、Memoryクラスで直接ネイティブメモリを管理します。
 *
 * メモリレイアウト（IPLAudioBuffer構造体）:
 * - offset 0: numChannels (int32, 4 bytes)
 * - offset 4: numSamples (int32, 4 bytes)
 * - offset 8: data (pointer64, 8 bytes) → float*[] へのポインタ
 *
 * 合計: 16バイト
 */
public class ManualAudioBuffer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int numChannels;
    private final int numSamples;

    // IPLAudioBuffer構造体用メモリ（16バイト）
    private final Memory structMemory;

    // float*配列用メモリ（各チャンネルのポインタを格納）
    private final Memory channelPointersMemory;

    // 各チャンネルのfloatデータ用メモリ
    private final Memory[] channelDataMemory;

    /**
     * 手動でIPLAudioBufferを構築
     *
     * @param numChannels チャンネル数（1=モノラル、2=ステレオ）
     * @param numSamples チャンネルあたりのサンプル数
     */
    public ManualAudioBuffer(int numChannels, int numSamples) {
        this.numChannels = numChannels;
        this.numSamples = numSamples;

        // 1. IPLAudioBuffer構造体用メモリ（16バイト）
        structMemory = new Memory(16);
        structMemory.clear(); // すべて0で初期化

        // 2. float*配列用メモリ（ポインタ配列）
        // ARM64ではポインタは8バイト
        channelPointersMemory = new Memory((long) numChannels * 8);

        // 3. 各チャンネルのfloatデータ用メモリ
        channelDataMemory = new Memory[numChannels];
        for (int i = 0; i < numChannels; i++) {
            // float = 4バイト
            channelDataMemory[i] = new Memory((long) numSamples * 4);
            channelDataMemory[i].clear();

            // channelPointersMemory[i] = channelDataMemory[i]のアドレス
            long offset = (long) i * 8; // ARM64ではポインタは8バイト
            channelPointersMemory.setPointer(offset, channelDataMemory[i]);
        }

        // 4. IPLAudioBuffer構造体を構築
        structMemory.setInt(0, numChannels);    // offset 0: numChannels
        structMemory.setInt(4, numSamples);     // offset 4: numSamples
        structMemory.setPointer(8, channelPointersMemory); // offset 8: data (float**)

        LOGGER.info("ManualAudioBuffer created: channels={}, samples={}, struct={}, pointers={}",
            numChannels, numSamples, structMemory, channelPointersMemory);
    }

    /**
     * IPLAudioBuffer構造体へのポインタを取得
     * iplBinauralEffectApply()などに渡す
     */
    public Pointer getStructPointer() {
        return structMemory;
    }

    /**
     * floatデータを書き込む（interleavedからdeinterleavedに変換）
     *
     * @param interleavedData インターリーブされたfloat配列
     */
    public void writeInterleavedData(float[] interleavedData) {
        if (interleavedData.length != numChannels * numSamples) {
            throw new IllegalArgumentException(
                String.format("Data size mismatch: expected %d, got %d",
                    numChannels * numSamples, interleavedData.length)
            );
        }

        // Interleavedからdeinterleavedに変換して書き込む
        for (int ch = 0; ch < numChannels; ch++) {
            for (int i = 0; i < numSamples; i++) {
                int interleavedIndex = i * numChannels + ch;
                float sample = interleavedData[interleavedIndex];
                channelDataMemory[ch].setFloat((long) i * 4, sample);
            }
        }
    }

    /**
     * floatデータを読み取る（deinterleavedからinterleavedに変換）
     *
     * @return インターリーブされたfloat配列
     */
    public float[] readInterleavedData() {
        float[] interleavedData = new float[numChannels * numSamples];

        // Deinterleavedからinterleavedに変換して読み取る
        for (int i = 0; i < numSamples; i++) {
            for (int ch = 0; ch < numChannels; ch++) {
                float sample = channelDataMemory[ch].getFloat((long) i * 4);
                int interleavedIndex = i * numChannels + ch;
                interleavedData[interleavedIndex] = sample;
            }
        }

        return interleavedData;
    }

    /**
     * モノラルデータを書き込む（チャンネル0のみ）
     *
     * @param monoData モノラルfloat配列
     */
    public void writeMonoData(float[] monoData) {
        if (numChannels != 1) {
            throw new IllegalStateException("This buffer is not mono");
        }
        if (monoData.length != numSamples) {
            throw new IllegalArgumentException(
                String.format("Data size mismatch: expected %d, got %d", numSamples, monoData.length)
            );
        }

        // チャンネル0にデータを書き込む
        channelDataMemory[0].write(0, monoData, 0, numSamples);
    }

    /**
     * デバッグ情報を出力
     */
    public void debugPrint(String label) {
        LOGGER.debug("{}: numChannels={}, numSamples={}", label, numChannels, numSamples);
        LOGGER.debug("  struct memory: {}", structMemory);
        LOGGER.debug("  struct.numChannels: {}", structMemory.getInt(0));
        LOGGER.debug("  struct.numSamples: {}", structMemory.getInt(4));
        LOGGER.debug("  struct.data (float**): {}", structMemory.getPointer(8));

        for (int i = 0; i < numChannels; i++) {
            Pointer channelPtr = channelPointersMemory.getPointer((long) i * 8);
            LOGGER.debug("  channel[{}] pointer: {}", i, channelPtr);

            // 最初の4サンプルを表示
            if (numSamples >= 4) {
                float s0 = channelDataMemory[i].getFloat(0);
                float s1 = channelDataMemory[i].getFloat(4);
                float s2 = channelDataMemory[i].getFloat(8);
                float s3 = channelDataMemory[i].getFloat(12);
                LOGGER.debug("    samples[0-3]: {}, {}, {}, {}", s0, s1, s2, s3);
            }
        }
    }

    /**
     * メモリを解放（ガベージコレクション任せでも良いが、明示的に解放可能）
     */
    public void dispose() {
        // JNA Memoryはファイナライザで自動解放されるが、
        // 明示的に参照を破棄することも可能
        LOGGER.debug("ManualAudioBuffer disposed: channels={}, samples={}", numChannels, numSamples);
    }

    public int getNumChannels() {
        return numChannels;
    }

    public int getNumSamples() {
        return numSamples;
    }
}
