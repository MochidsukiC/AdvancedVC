package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;

/**
 * Steam Audio (Phonon) JNA bindings (minimal subset used by this project).
 * ASCII-only comments to avoid encoding issues.
 */
public interface SteamAudioLibrary extends Library {

    SteamAudioLibrary INSTANCE = Native.load("phonon", SteamAudioLibrary.class);

    // Version (Steam Audio 4.7.0)
    int STEAMAUDIO_VERSION_MAJOR = 4;
    int STEAMAUDIO_VERSION_MINOR = 7;
    int STEAMAUDIO_VERSION_PATCH = 0;
    int STEAMAUDIO_VERSION = (STEAMAUDIO_VERSION_MAJOR << 16)
            | (STEAMAUDIO_VERSION_MINOR << 8)
            | STEAMAUDIO_VERSION_PATCH;

    int IPL_FALSE = 0;
    int IPL_TRUE = 1;

    interface IPLerror {
        int IPL_STATUS_SUCCESS = 0;
        int IPL_STATUS_FAILURE = 1;
        int IPL_STATUS_OUTOFMEMORY = 2;
        int IPL_STATUS_INITIALIZATION = 3;
    }

    interface IPLAudioFormat {
        int IPL_AUDIOFORMAT_PCM = 0;
        int IPL_AUDIOFORMAT_FLOAT32 = 1;
    }

    interface IPLChannelLayoutType {
        int IPL_CHANNELLAYOUTTYPE_SPEAKERS = 0;
        int IPL_CHANNELLAYOUTTYPE_AMBISONICS = 1;
    }

    interface IPLChannelOrder {
        int IPL_CHANNELORDER_INTERLEAVED = 0;
        int IPL_CHANNELORDER_DEINTERLEAVED = 1;
    }

    interface IPLHRTFType {
        int IPL_HRTFTYPE_DEFAULT = 0;
        int IPL_HRTFTYPE_SOFA = 1;
    }

    // Basic types
    class IPLVector3 extends Structure {
        public float x;
        public float y;
        public float z;

        public IPLVector3() {}

        public IPLVector3(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("x", "y", "z");
        }
    }

    class IPLContextSettings extends Structure {
        public int version;
        public Pointer logCallback;
        public Pointer allocateCallback;
        public Pointer freeCallback;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("version", "logCallback", "allocateCallback", "freeCallback");
        }
    }

    class IPLHRTFSettings extends Structure {
        public int type;
        public Pointer sofaFileName;
        public Pointer sofaData;
        public int sofaDataSize;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("type", "sofaFileName", "sofaData", "sofaDataSize");
        }
    }

    class IPLAudioSettings extends Structure {
        public int samplingRate;
        public int frameSize;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("samplingRate", "frameSize");
        }
    }

    class IPLAudioBufferFormat extends Structure {
        public int channelLayoutType;
        public int channelOrder;
        public int numChannels;
        public int numSamples;
        public int sampleRate;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("channelLayoutType", "channelOrder", "numChannels", "numSamples", "sampleRate");
        }
    }

    // Matches C struct: struct IPLAudioBuffer { int numChannels; int numSamples; float** data; }
    @Structure.FieldOrder({"format", "data"})
    class IPLAudioBuffer extends Structure {
        public IPLAudioBufferFormat format;
        public Pointer data;

        public IPLAudioBuffer() { super(Structure.ALIGN_DEFAULT); }
        public IPLAudioBuffer(Pointer p) { super(p, Structure.ALIGN_DEFAULT); read(); }
    }
    class IPLBinauralEffectSettings extends Structure {
        public Pointer hrtf;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf");
        }
    }

    class IPLBinauralEffectParams extends Structure {
        public IPLVector3 direction;
        public int interpolation;
        public float spatialBlend;
        public Pointer hrtf;
        public Pointer peakDelays;

        public IPLBinauralEffectParams() { super(); direction = new IPLVector3(); }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("direction", "interpolation", "spatialBlend", "hrtf", "peakDelays");
        }
    }

    // C API functions used
    int iplContextCreate(IPLContextSettings settings, PointerByReference context);
    void iplContextRelease(PointerByReference context);

    int iplHRTFCreate(Pointer context, IPLAudioSettings audioSettings, IPLHRTFSettings hrtfSettings, PointerByReference hrtf);
    void iplHRTFRelease(PointerByReference hrtf);

    int iplBinauralEffectCreate(Pointer context, IPLAudioSettings audioSettings, IPLBinauralEffectSettings effectSettings, PointerByReference effect);
    void iplBinauralEffectRelease(PointerByReference effect);
    int iplBinauralEffectApply(Pointer effect, Pointer params, Pointer inBuffer, Pointer outBuffer);

    int iplAudioBufferAllocate(Pointer context, int numChannels, int numSamples, Pointer buffer);
    void iplAudioBufferFree(Pointer context, Pointer buffer);
    void iplAudioBufferInterleave(Pointer context, Pointer src, float[] dst);
    void iplAudioBufferDeinterleave(Pointer context, float[] src, Pointer dst);
}
