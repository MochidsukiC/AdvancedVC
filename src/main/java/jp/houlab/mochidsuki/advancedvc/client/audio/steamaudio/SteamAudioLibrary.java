package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

/**
 * Steam Audio (Phonon) JNAインターフェース
 * Phase 1: 基本的なバイノーラル再生に必要な機能のみ
 */
public interface SteamAudioLibrary extends Library {

    SteamAudioLibrary INSTANCE = Native.load("phonon", SteamAudioLibrary.class);

    // ========================================
    // 基本型定義
    // ========================================

    // Steam Audio v4.7.0バージョン定数
    int STEAMAUDIO_VERSION_MAJOR = 4;
    int STEAMAUDIO_VERSION_MINOR = 7;
    int STEAMAUDIO_VERSION_PATCH = 0;
    int STEAMAUDIO_VERSION = (STEAMAUDIO_VERSION_MAJOR << 16) | (STEAMAUDIO_VERSION_MINOR << 8) | STEAMAUDIO_VERSION_PATCH;

    int IPL_FALSE = 0;
    int IPL_TRUE = 1;

    // エラーコード
    interface IPLerror {
        int IPL_STATUS_SUCCESS = 0;
        int IPL_STATUS_FAILURE = 1;
        int IPL_STATUS_OUTOFMEMORY = 2;
        int IPL_STATUS_INITIALIZATION = 3;
    }

    // オーディオフォーマット
    interface IPLAudioFormat {
        int IPL_AUDIOFORMAT_PCM = 0;
        int IPL_AUDIOFORMAT_FLOAT32 = 1;
    }

    // チャンネルレイアウト
    interface IPLChannelLayoutType {
        int IPL_CHANNELLAYOUTTYPE_SPEAKERS = 0;
        int IPL_CHANNELLAYOUTTYPE_AMBISONICS = 1;
    }

    // チャンネル順序
    interface IPLChannelOrder {
        int IPL_CHANNELORDER_INTERLEAVED = 0;
        int IPL_CHANNELORDER_DEINTERLEAVED = 1;
    }

    // HRTFタイプ
    interface IPLHRTFType {
        int IPL_HRTFTYPE_DEFAULT = 0;
        int IPL_HRTFTYPE_SOFA = 1;
    }

    // ========================================
    // 構造体定義
    // ========================================

    /**
     * 3Dベクトル
     */
    class IPLVector3 extends Structure {
        public float x;
        public float y;
        public float z;

        public IPLVector3() {}

        public IPLVector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("x", "y", "z");
        }
    }

    /**
     * コンテキスト設定
     */
    class IPLContextSettings extends Structure {
        public int version;           // API version (IPL_VERSION)
        public Pointer logCallback;   // Callback for logging (optional)
        public Pointer allocateCallback;  // Custom allocator (optional)
        public Pointer freeCallback;      // Custom free (optional)

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("version", "logCallback", "allocateCallback", "freeCallback");
        }
    }

    /**
     * HRTF設定
     */
    class IPLHRTFSettings extends Structure {
        public int type;              // IPLHRTFType
        public Pointer sofaFileName;  // Path to SOFA file (if type == SOFA)
        public Pointer sofaData;      // SOFA file data (if type == SOFA)
        public int sofaDataSize;      // Size of SOFA data

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("type", "sofaFileName", "sofaData", "sofaDataSize");
        }
    }

    /**
     * オーディオ設定
     */
    class IPLAudioSettings extends Structure {
        public int samplingRate;      // Sampling rate (Hz)
        public int frameSize;         // Frame size (samples)

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("samplingRate", "frameSize");
        }
    }

    /**
     * オーディオバッファフォーマット
     */
    class IPLAudioBufferFormat extends Structure {
        public int channelLayoutType; // IPLChannelLayoutType
        public int channelOrder;      // IPLChannelOrder
        public int numChannels;       // Number of channels
        public int numSamples;        // Number of samples per channel
        public int sampleRate;        // Sample rate (Hz)

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("channelLayoutType", "channelOrder", "numChannels", "numSamples", "sampleRate");
        }
    }

    /**
     * オーディオバッファ（Deinterleaved形式）
     * dataはIPLfloat32** (チャンネルごとのfloat配列へのポインタの配列)
     *
     * ARM64環境での構造体レイアウト:
     * - numChannels: 4バイト (offset 0)
     * - numSamples: 4バイト (offset 4)
     * - data: 8バイト (offset 8, パディングなし)
     */
    @Structure.FieldOrder({"numChannels", "numSamples", "data"})
    class IPLAudioBuffer extends Structure {
        public int numChannels;     // Number of channels
        public int numSamples;      // Number of samples per channel
        public Pointer data;        // Array of channel pointers (float**)

        public IPLAudioBuffer() {
            super(Structure.ALIGN_DEFAULT);
        }

        public IPLAudioBuffer(Pointer p) {
            super(p, Structure.ALIGN_DEFAULT);
            read();
        }

        @Override
        public int size() {
            // ARM64: int(4) + int(4) + Pointer(8) = 16バイト
            return 16;
        }
    }

    /**
     * バイノーラルエフェクト設定
     */
    class IPLBinauralEffectSettings extends Structure {
        public Pointer hrtf;  // IPLHRTFオブジェクト

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf");
        }
    }

    /**
     * バイノーラルエフェクトパラメータ
     */
    class IPLBinauralEffectParams extends Structure {
        public IPLVector3 direction;  // Source direction
        public int interpolation;     // Interpolation mode
        public float spatialBlend;    // Spatial blend (0.0 = mono, 1.0 = full 3D)

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("direction", "interpolation", "spatialBlend");
        }
    }

    // ========================================
    // API関数
    // ========================================

    /**
     * コンテキストを作成
     */
    int iplContextCreate(IPLContextSettings settings, PointerByReference context);

    /**
     * コンテキストを解放
     */
    void iplContextRelease(PointerByReference context);

    /**
     * HRTFを作成
     */
    int iplHRTFCreate(Pointer context, IPLAudioSettings audioSettings, IPLHRTFSettings hrtfSettings, PointerByReference hrtf);

    /**
     * HRTFを解放
     */
    void iplHRTFRelease(PointerByReference hrtf);

    /**
     * バイノーラルエフェクトを作成
     */
    int iplBinauralEffectCreate(Pointer context, IPLAudioSettings audioSettings, IPLBinauralEffectSettings effectSettings, PointerByReference effect);

    /**
     * バイノーラルエフェクトを解放
     */
    void iplBinauralEffectRelease(PointerByReference effect);

    /**
     * バイノーラルエフェクトを適用
     * Note: IPLAudioBuffer はポインタとして渡す必要がある（by-reference）
     */
    int iplBinauralEffectApply(Pointer effect, IPLBinauralEffectParams params, Pointer inBuffer, Pointer outBuffer);

    /**
     * オーディオバッファを割り当て
     */
    int iplAudioBufferAllocate(Pointer context, int numChannels, int numSamples, Pointer buffer);

    /**
     * オーディオバッファを解放
     */
    void iplAudioBufferFree(Pointer context, Pointer buffer);

    /**
     * deinterleavedバッファからinterleavedバッファへ変換
     */
    void iplAudioBufferInterleave(Pointer context, Pointer src, float[] dst);

    /**
     * interleavedバッファからdeinterleavedバッファへ変換
     */
    void iplAudioBufferDeinterleave(Pointer context, float[] src, Pointer dst);
}
