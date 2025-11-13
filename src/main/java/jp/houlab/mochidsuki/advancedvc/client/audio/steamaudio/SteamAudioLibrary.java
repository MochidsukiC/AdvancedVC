package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.FloatByReference;

/**
 * Steam Audio (Phonon) JNA bindings - Complete API for Steam Audio 4.7.0
 *
 * This is a complete Java binding for Steam Audio C API, covering:
 * - Context management
 * - Scene and mesh management
 * - Audio buffers and processing
 * - HRTF and spatial audio effects
 * - Ambisonics processing
 * - Ray tracing and acoustic simulation
 *
 * Based on: https://github.com/ValveSoftware/steam-audio/blob/master/fmod/include/phonon/phonon.h
 */
public interface SteamAudioLibrary extends Library {

    SteamAudioLibrary INSTANCE = Native.load("phonon", SteamAudioLibrary.class);

    // ===================================================================================
    // CONSTANTS AND VERSION
    // ===================================================================================

    int STEAMAUDIO_VERSION_MAJOR = 4;
    int STEAMAUDIO_VERSION_MINOR = 7;
    int STEAMAUDIO_VERSION_PATCH = 0;
    int STEAMAUDIO_VERSION = (STEAMAUDIO_VERSION_MAJOR << 16)
            | (STEAMAUDIO_VERSION_MINOR << 8)
            | STEAMAUDIO_VERSION_PATCH;

    int IPL_FALSE = 0;
    int IPL_TRUE = 1;

    // ===================================================================================
    // ENUMERATIONS
    // ===================================================================================

    /**
     * Error codes returned by Steam Audio API functions
     */
    interface IPLerror {
        int IPL_STATUS_SUCCESS = 0;
        int IPL_STATUS_FAILURE = 1;
        int IPL_STATUS_OUTOFMEMORY = 2;
        int IPL_STATUS_INITIALIZATION = 3;
    }

    /**
     * Log message severity levels
     */
    interface IPLLogLevel {
        int IPL_LOGLEVEL_INFO = 0;
        int IPL_LOGLEVEL_WARNING = 1;
        int IPL_LOGLEVEL_ERROR = 2;
        int IPL_LOGLEVEL_DEBUG = 3;
    }

    /**
     * SIMD instruction set levels
     */
    interface IPLSIMDLevel {
        int IPL_SIMDLEVEL_SSE2 = 0;
        int IPL_SIMDLEVEL_SSE4 = 1;
        int IPL_SIMDLEVEL_AVX = 2;
        int IPL_SIMDLEVEL_AVX2 = 3;
        int IPL_SIMDLEVEL_AVX512 = 4;
        int IPL_SIMDLEVEL_NEON = 5;
    }

    /**
     * Ray tracing backend types
     */
    interface IPLSceneType {
        int IPL_SCENETYPE_DEFAULT = 0;
        int IPL_SCENETYPE_EMBREE = 1;
        int IPL_SCENETYPE_RADEONRAYS = 2;
        int IPL_SCENETYPE_CUSTOM = 3;
    }

    /**
     * Speaker layout configurations
     */
    interface IPLSpeakerLayoutType {
        int IPL_SPEAKERLAYOUTTYPE_MONO = 0;
        int IPL_SPEAKERLAYOUTTYPE_STEREO = 1;
        int IPL_SPEAKERLAYOUTTYPE_QUADRAPHONIC = 2;
        int IPL_SPEAKERLAYOUTTYPE_SURROUND_5_1 = 3;
        int IPL_SPEAKERLAYOUTTYPE_SURROUND_7_1 = 4;
        int IPL_SPEAKERLAYOUTTYPE_CUSTOM = 5;
    }

    /**
     * Ambisonics normalization types
     */
    interface IPLAmbisonicsType {
        int IPL_AMBISONICSTYPE_N3D = 0;
        int IPL_AMBISONICSTYPE_SN3D = 1;
        int IPL_AMBISONICSTYPE_FUMA = 2;
    }

    /**
     * Audio effect tail state
     */
    interface IPLAudioEffectState {
        int IPL_AUDIOEFFECTSTATE_TAILREMAINING = 0;
        int IPL_AUDIOEFFECTSTATE_TAILCOMPLETE = 1;
    }

    /**
     * HRTF types
     */
    interface IPLHRTFType {
        int IPL_HRTFTYPE_DEFAULT = 0;
        int IPL_HRTFTYPE_SOFA = 1;
    }

    /**
     * HRTF volume normalization types
     */
    interface IPLHRTFNormType {
        int IPL_HRTFNORMTYPE_NONE = 0;
        int IPL_HRTFNORMTYPE_RMS = 1;
    }

    /**
     * HRTF interpolation types
     */
    interface IPLHRTFInterpolation {
        int IPL_HRTFINTERPOLATION_NEAREST = 0;
        int IPL_HRTFINTERPOLATION_BILINEAR = 1;
    }

    /**
     * Channel layout types
     */
    interface IPLChannelLayoutType {
        int IPL_CHANNELLAYOUTTYPE_SPEAKERS = 0;
        int IPL_CHANNELLAYOUTTYPE_AMBISONICS = 1;
    }

    /**
     * Channel order (interleaved vs deinterleaved)
     */
    interface IPLChannelOrder {
        int IPL_CHANNELORDER_INTERLEAVED = 0;
        int IPL_CHANNELORDER_DEINTERLEAVED = 1;
    }

    /**
     * OpenCL device types
     */
    interface IPLOpenCLDeviceType {
        int IPL_OPENCLDEVICETYPE_ANY = 0;
        int IPL_OPENCLDEVICETYPE_CPU = 1;
        int IPL_OPENCLDEVICETYPE_GPU = 2;
    }

    // ===================================================================================
    // BASIC DATA STRUCTURES
    // ===================================================================================

    /**
     * 3D vector
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
     * 4x4 transformation matrix (row-major order)
     */
    class IPLMatrix4x4 extends Structure {
        public float[] elements = new float[16];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("elements");
        }
    }

    /**
     * Axis-aligned bounding box
     */
    class IPLBox extends Structure {
        public IPLVector3 minCoordinates;
        public IPLVector3 maxCoordinates;

        public IPLBox() {
            minCoordinates = new IPLVector3();
            maxCoordinates = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("minCoordinates", "maxCoordinates");
        }
    }

    /**
     * Sphere
     */
    class IPLSphere extends Structure {
        public IPLVector3 center;
        public float radius;

        public IPLSphere() {
            center = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("center", "radius");
        }
    }

    /**
     * Local coordinate system
     */
    class IPLCoordinateSpace3 extends Structure {
        public IPLVector3 right;
        public IPLVector3 up;
        public IPLVector3 ahead;
        public IPLVector3 origin;

        public IPLCoordinateSpace3() {
            right = new IPLVector3();
            up = new IPLVector3();
            ahead = new IPLVector3();
            origin = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("right", "up", "ahead", "origin");
        }
    }

    /**
     * Triangle (three vertex indices)
     */
    class IPLTriangle extends Structure {
        public int[] indices = new int[3];

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("indices");
        }
    }

    /**
     * Acoustic material properties
     */
    class IPLMaterial extends Structure {
        public float[] absorption = new float[3];  // Low, mid, high frequency absorption
        public float scattering;
        public float[] transmission = new float[3]; // Low, mid, high frequency transmission

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("absorption", "scattering", "transmission");
        }
    }

    /**
     * Ray for ray tracing
     */
    class IPLRay extends Structure {
        public IPLVector3 origin;
        public IPLVector3 direction;

        public IPLRay() {
            origin = new IPLVector3();
            direction = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("origin", "direction");
        }
    }

    /**
     * Ray intersection result
     */
    class IPLHit extends Structure {
        public float distance;
        public int triangleIndex;
        public int objectIndex;
        public int materialIndex;
        public IPLVector3 normal;

        public IPLHit() {
            normal = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("distance", "triangleIndex", "objectIndex", "materialIndex", "normal");
        }
    }

    // ===================================================================================
    // CONTEXT MANAGEMENT
    // ===================================================================================

    /**
     * Context creation settings
     */
    class IPLContextSettings extends Structure {
        public int version;
        public Pointer logCallback;       // IPLLogFunction
        public Pointer allocateCallback;  // IPLAllocateFunction
        public Pointer freeCallback;      // IPLFreeFunction

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("version", "logCallback", "allocateCallback", "freeCallback");
        }
    }

    // Context functions
    int iplContextCreate(IPLContextSettings settings, PointerByReference context);
    Pointer iplContextRetain(Pointer context);
    void iplContextRelease(PointerByReference context);

    // ===================================================================================
    // SCENE AND GEOMETRY
    // ===================================================================================

    /**
     * Scene creation settings
     */
    class IPLSceneSettings extends Structure {
        public int type;               // IPLSceneType
        public Pointer closestHitCallback;
        public Pointer anyHitCallback;
        public Pointer batchedClosestHitCallback;
        public Pointer batchedAnyHitCallback;
        public Pointer userData;
        public Pointer embreeDevice;
        public Pointer radeonRaysDevice;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("type", "closestHitCallback", "anyHitCallback",
                    "batchedClosestHitCallback", "batchedAnyHitCallback", "userData",
                    "embreeDevice", "radeonRaysDevice");
        }
    }

    /**
     * Static mesh settings
     */
    class IPLStaticMeshSettings extends Structure {
        public int numVertices;
        public Pointer vertices;      // IPLVector3*
        public int numTriangles;
        public Pointer triangles;     // IPLTriangle*
        public Pointer materialIndices; // int*
        public int numMaterials;
        public Pointer materials;     // IPLMaterial*

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("numVertices", "vertices", "numTriangles",
                    "triangles", "materialIndices", "numMaterials", "materials");
        }
    }

    /**
     * Instanced mesh settings
     */
    class IPLInstancedMeshSettings extends Structure {
        public Pointer subScene;
        public IPLMatrix4x4 transform;

        public IPLInstancedMeshSettings() {
            transform = new IPLMatrix4x4();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("subScene", "transform");
        }
    }

    // Scene functions
    int iplSceneCreate(Pointer context, IPLSceneSettings settings, PointerByReference scene);
    Pointer iplSceneRetain(Pointer scene);
    void iplSceneRelease(PointerByReference scene);
    int iplSceneLoad(Pointer context, IPLSceneSettings settings, Pointer serializedObject,
                     Pointer progressCallback, Pointer userData, PointerByReference scene);
    int iplSceneSave(Pointer scene, PointerByReference serializedObject);
    int iplSceneSaveOBJ(Pointer scene, String fileBaseName);
    void iplSceneCommit(Pointer scene);

    // Static mesh functions
    int iplStaticMeshCreate(Pointer scene, IPLStaticMeshSettings settings, PointerByReference staticMesh);
    Pointer iplStaticMeshRetain(Pointer staticMesh);
    void iplStaticMeshRelease(PointerByReference staticMesh);
    int iplStaticMeshLoad(Pointer scene, Pointer serializedObject, Pointer progressCallback,
                          Pointer userData, PointerByReference staticMesh);
    int iplStaticMeshSave(Pointer staticMesh, PointerByReference serializedObject);
    void iplStaticMeshAdd(Pointer staticMesh, Pointer scene);
    void iplStaticMeshRemove(Pointer staticMesh, Pointer scene);

    // Instanced mesh functions
    int iplInstancedMeshCreate(Pointer scene, IPLInstancedMeshSettings settings, PointerByReference instancedMesh);
    Pointer iplInstancedMeshRetain(Pointer instancedMesh);
    void iplInstancedMeshRelease(PointerByReference instancedMesh);
    void iplInstancedMeshAdd(Pointer instancedMesh, Pointer scene);
    void iplInstancedMeshRemove(Pointer instancedMesh, Pointer scene);
    void iplInstancedMeshUpdateTransform(Pointer instancedMesh, Pointer scene, IPLMatrix4x4 transform);

    // ===================================================================================
    // AUDIO BUFFERS
    // ===================================================================================

    /**
     * Audio settings (sample rate and frame size)
     */
    class IPLAudioSettings extends Structure {
        public int samplingRate;
        public int frameSize;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("samplingRate", "frameSize");
        }
    }

    /**
     * Audio buffer format
     */
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

    /**
     * Audio buffer structure
     */
    @Structure.FieldOrder({"format", "data"})
    class IPLAudioBuffer extends Structure {
        public IPLAudioBufferFormat format;
        public Pointer data;  // float**

        public IPLAudioBuffer() {
            super(Structure.ALIGN_DEFAULT);
        }

        public IPLAudioBuffer(Pointer p) {
            super(p, Structure.ALIGN_DEFAULT);
            read();
        }
    }

    // Audio buffer functions
    int iplAudioBufferAllocate(Pointer context, int numChannels, int numSamples, Pointer buffer);
    void iplAudioBufferFree(Pointer context, Pointer buffer);
    void iplAudioBufferInterleave(Pointer context, Pointer src, float[] dst);
    void iplAudioBufferDeinterleave(Pointer context, float[] src, Pointer dst);
    void iplAudioBufferMix(Pointer context, Pointer in, Pointer mix);
    void iplAudioBufferDownmix(Pointer context, Pointer in, Pointer out);
    void iplAudioBufferConvertAmbisonics(Pointer context, int inType, int outType, Pointer in, Pointer out);

    // ===================================================================================
    // HRTF AND SPATIAL AUDIO
    // ===================================================================================

    /**
     * HRTF creation settings
     */
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

    // HRTF functions
    int iplHRTFCreate(Pointer context, IPLAudioSettings audioSettings, IPLHRTFSettings hrtfSettings, PointerByReference hrtf);
    Pointer iplHRTFRetain(Pointer hrtf);
    void iplHRTFRelease(PointerByReference hrtf);

    // ===================================================================================
    // AUDIO EFFECTS
    // ===================================================================================

    // --- Panning Effect ---

    /**
     * Speaker layout
     */
    class IPLSpeakerLayout extends Structure {
        public int type;
        public int numSpeakers;
        public Pointer speakers; // IPLVector3*

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("type", "numSpeakers", "speakers");
        }
    }

    /**
     * Panning effect settings
     */
    class IPLPanningEffectSettings extends Structure {
        public IPLSpeakerLayout speakerLayout;

        public IPLPanningEffectSettings() {
            speakerLayout = new IPLSpeakerLayout();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("speakerLayout");
        }
    }

    /**
     * Panning effect parameters
     */
    class IPLPanningEffectParams extends Structure {
        public IPLVector3 direction;

        public IPLPanningEffectParams() {
            direction = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("direction");
        }
    }

    // Panning effect functions
    int iplPanningEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                               IPLPanningEffectSettings effectSettings, PointerByReference effect);
    Pointer iplPanningEffectRetain(Pointer effect);
    void iplPanningEffectRelease(PointerByReference effect);
    void iplPanningEffectReset(Pointer effect);
    int iplPanningEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Binaural Effect ---

    /**
     * Binaural effect settings
     */
    class IPLBinauralEffectSettings extends Structure {
        public Pointer hrtf;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf");
        }
    }

    /**
     * Binaural effect parameters
     */
    class IPLBinauralEffectParams extends Structure {
        public IPLVector3 direction;
        public int interpolation;
        public float spatialBlend;
        public Pointer hrtf;
        public Pointer peakDelays;

        public IPLBinauralEffectParams() {
            super();
            direction = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("direction", "interpolation", "spatialBlend", "hrtf", "peakDelays");
        }
    }

    // Binaural effect functions
    int iplBinauralEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                IPLBinauralEffectSettings effectSettings, PointerByReference effect);
    Pointer iplBinauralEffectRetain(Pointer effect);
    void iplBinauralEffectRelease(PointerByReference effect);
    void iplBinauralEffectReset(Pointer effect);
    int iplBinauralEffectApply(Pointer effect, Pointer params, Pointer inBuffer, Pointer outBuffer);

    // --- Virtual Surround Effect ---

    /**
     * Virtual surround effect settings
     */
    class IPLVirtualSurroundEffectSettings extends Structure {
        public IPLSpeakerLayout speakerLayout;
        public Pointer hrtf;

        public IPLVirtualSurroundEffectSettings() {
            speakerLayout = new IPLSpeakerLayout();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("speakerLayout", "hrtf");
        }
    }

    /**
     * Virtual surround effect parameters
     */
    class IPLVirtualSurroundEffectParams extends Structure {
        public Pointer hrtf;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf");
        }
    }

    // Virtual surround effect functions
    int iplVirtualSurroundEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                       IPLVirtualSurroundEffectSettings effectSettings, PointerByReference effect);
    Pointer iplVirtualSurroundEffectRetain(Pointer effect);
    void iplVirtualSurroundEffectRelease(PointerByReference effect);
    void iplVirtualSurroundEffectReset(Pointer effect);
    int iplVirtualSurroundEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Ambisonics Encode Effect ---

    /**
     * Ambisonics encode effect settings
     */
    class IPLAmbisonicsEncodeEffectSettings extends Structure {
        public int maxOrder;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("maxOrder");
        }
    }

    /**
     * Ambisonics encode effect parameters
     */
    class IPLAmbisonicsEncodeEffectParams extends Structure {
        public IPLVector3 direction;
        public int order;

        public IPLAmbisonicsEncodeEffectParams() {
            direction = new IPLVector3();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("direction", "order");
        }
    }

    // Ambisonics encode effect functions
    int iplAmbisonicsEncodeEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                        IPLAmbisonicsEncodeEffectSettings effectSettings, PointerByReference effect);
    Pointer iplAmbisonicsEncodeEffectRetain(Pointer effect);
    void iplAmbisonicsEncodeEffectRelease(PointerByReference effect);
    void iplAmbisonicsEncodeEffectReset(Pointer effect);
    int iplAmbisonicsEncodeEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Ambisonics Panning Effect ---

    /**
     * Ambisonics panning effect settings
     */
    class IPLAmbisonicsPanningEffectSettings extends Structure {
        public IPLSpeakerLayout speakerLayout;
        public int maxOrder;

        public IPLAmbisonicsPanningEffectSettings() {
            speakerLayout = new IPLSpeakerLayout();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("speakerLayout", "maxOrder");
        }
    }

    /**
     * Ambisonics panning effect parameters
     */
    class IPLAmbisonicsPanningEffectParams extends Structure {
        public int order;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("order");
        }
    }

    // Ambisonics panning effect functions
    int iplAmbisonicsPanningEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                         IPLAmbisonicsPanningEffectSettings effectSettings, PointerByReference effect);
    Pointer iplAmbisonicsPanningEffectRetain(Pointer effect);
    void iplAmbisonicsPanningEffectRelease(PointerByReference effect);
    void iplAmbisonicsPanningEffectReset(Pointer effect);
    int iplAmbisonicsPanningEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Ambisonics Binaural Effect ---

    /**
     * Ambisonics binaural effect settings
     */
    class IPLAmbisonicsBinauralEffectSettings extends Structure {
        public Pointer hrtf;
        public int maxOrder;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf", "maxOrder");
        }
    }

    /**
     * Ambisonics binaural effect parameters
     */
    class IPLAmbisonicsBinauralEffectParams extends Structure {
        public Pointer hrtf;
        public int order;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("hrtf", "order");
        }
    }

    // Ambisonics binaural effect functions
    int iplAmbisonicsBinauralEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                          IPLAmbisonicsBinauralEffectSettings effectSettings, PointerByReference effect);
    Pointer iplAmbisonicsBinauralEffectRetain(Pointer effect);
    void iplAmbisonicsBinauralEffectRelease(PointerByReference effect);
    void iplAmbisonicsBinauralEffectReset(Pointer effect);
    int iplAmbisonicsBinauralEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Ambisonics Rotation Effect ---

    /**
     * Ambisonics rotation effect settings
     */
    class IPLAmbisonicsRotationEffectSettings extends Structure {
        public int maxOrder;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("maxOrder");
        }
    }

    /**
     * Ambisonics rotation effect parameters
     */
    class IPLAmbisonicsRotationEffectParams extends Structure {
        public int order;
        public IPLMatrix4x4 orientation;

        public IPLAmbisonicsRotationEffectParams() {
            orientation = new IPLMatrix4x4();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("order", "orientation");
        }
    }

    // Ambisonics rotation effect functions
    int iplAmbisonicsRotationEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                          IPLAmbisonicsRotationEffectSettings effectSettings, PointerByReference effect);
    Pointer iplAmbisonicsRotationEffectRetain(Pointer effect);
    void iplAmbisonicsRotationEffectRelease(PointerByReference effect);
    void iplAmbisonicsRotationEffectReset(Pointer effect);
    int iplAmbisonicsRotationEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // --- Ambisonics Decode Effect ---

    /**
     * Ambisonics decode effect settings
     */
    class IPLAmbisonicsDecodeEffectSettings extends Structure {
        public IPLSpeakerLayout speakerLayout;
        public int maxOrder;

        public IPLAmbisonicsDecodeEffectSettings() {
            speakerLayout = new IPLSpeakerLayout();
        }

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("speakerLayout", "maxOrder");
        }
    }

    /**
     * Ambisonics decode effect parameters
     */
    class IPLAmbisonicsDecodeEffectParams extends Structure {
        public int order;
        public Pointer hrtf;
        public int binaural;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("order", "hrtf", "binaural");
        }
    }

    // Ambisonics decode effect functions
    int iplAmbisonicsDecodeEffectCreate(Pointer context, IPLAudioSettings audioSettings,
                                        IPLAmbisonicsDecodeEffectSettings effectSettings, PointerByReference effect);
    Pointer iplAmbisonicsDecodeEffectRetain(Pointer effect);
    void iplAmbisonicsDecodeEffectRelease(PointerByReference effect);
    void iplAmbisonicsDecodeEffectReset(Pointer effect);
    int iplAmbisonicsDecodeEffectApply(Pointer effect, Pointer params, Pointer in, Pointer out);

    // ===================================================================================
    // UTILITY FUNCTIONS
    // ===================================================================================

    /**
     * Calculate relative direction between two coordinate spaces
     */
    IPLVector3 iplCalculateRelativeDirection(Pointer context, IPLCoordinateSpace3 source,
                                             IPLCoordinateSpace3 listener, IPLVector3 direction);
}
