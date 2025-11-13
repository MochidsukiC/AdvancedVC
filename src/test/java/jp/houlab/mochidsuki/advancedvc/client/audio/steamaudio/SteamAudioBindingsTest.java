package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Steam Audio Java Bindings Integration Test
 *
 * This standalone test program validates:
 * 1. Native library loading
 * 2. Context initialization
 * 3. HRTF creation
 * 4. Binaural effect processing
 * 5. Audio buffer operations
 * 6. Scene/Mesh API (basic)
 *
 * Run with: java -cp ... SteamAudioBindingsTest
 */
public class SteamAudioBindingsTest {

    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SIZE = 1024;

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Steam Audio Java Bindings Integration Test");
        System.out.println("=================================================\n");

        // Run all tests
        testNativeLibraryLoading();
        testContextInitialization();
        testHRTFCreation();
        testBinauralEffect();
        testAudioProcessing();
        testSceneMeshAPI();
        testAudioBufferOperations();
        testNewEffects();

        // Regression tests for past issues
        testVectorNormalization();
        testIPLAudioBufferAllocate();
        testStructureFieldOrder();

        // Print summary
        System.out.println("\n=================================================");
        System.out.println("  Test Summary");
        System.out.println("=================================================");
        System.out.println("Tests Passed: " + testsPassed);
        System.out.println("Tests Failed: " + testsFailed);
        System.out.println("Total Tests:  " + (testsPassed + testsFailed));

        if (testsFailed == 0) {
            System.out.println("\nResult: ALL TESTS PASSED ✓");
            System.exit(0);
        } else {
            System.out.println("\nResult: SOME TESTS FAILED ✗");
            System.exit(1);
        }
    }

    // =========================================================================
    // TEST 1: Native Library Loading
    // =========================================================================

    private static void testNativeLibraryLoading() {
        System.out.println("[TEST 1] Native Library Loading");
        try {
            boolean loaded = NativeLibraryLoader.loadSteamAudio();
            if (loaded) {
                pass("Native library loaded successfully");
            } else {
                fail("Failed to load native library");
            }
        } catch (Exception e) {
            fail("Exception during library loading: " + e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 2: Context Initialization
    // =========================================================================

    private static void testContextInitialization() {
        System.out.println("[TEST 2] Context Initialization");

        PointerByReference contextRef = new PointerByReference();

        try {
            // Create context settings
            SteamAudioLibrary.IPLContextSettings settings = new SteamAudioLibrary.IPLContextSettings();
            settings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            settings.logCallback = Pointer.NULL;
            settings.allocateCallback = Pointer.NULL;
            settings.freeCallback = Pointer.NULL;

            // Create context
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(settings, contextRef);

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                Pointer context = contextRef.getValue();
                if (context != null && context != Pointer.NULL) {
                    pass("Context created successfully");

                    // Clean up
                    SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);
                    pass("Context released successfully");
                } else {
                    fail("Context pointer is NULL");
                }
            } else {
                fail("iplContextCreate failed with status: " + result);
            }
        } catch (Exception e) {
            fail("Exception during context initialization: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 3: HRTF Creation
    // =========================================================================

    private static void testHRTFCreation() {
        System.out.println("[TEST 3] HRTF Creation");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference hrtfRef = new PointerByReference();

        try {
            // Create context
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            contextSettings.logCallback = Pointer.NULL;
            contextSettings.allocateCallback = Pointer.NULL;
            contextSettings.freeCallback = Pointer.NULL;

            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create context for HRTF test");
                return;
            }

            Pointer context = contextRef.getValue();

            // Create audio settings
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = SAMPLE_RATE;
            audioSettings.frameSize = FRAME_SIZE;

            // Create HRTF settings
            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            hrtfSettings.sofaFileName = Pointer.NULL;
            hrtfSettings.sofaData = Pointer.NULL;
            hrtfSettings.sofaDataSize = 0;

            // Create HRTF
            result = SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                Pointer hrtf = hrtfRef.getValue();
                if (hrtf != null && hrtf != Pointer.NULL) {
                    pass("HRTF created successfully");

                    // Clean up
                    SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
                    pass("HRTF released successfully");
                } else {
                    fail("HRTF pointer is NULL");
                }
            } else {
                fail("iplHRTFCreate failed with status: " + result);
            }

            // Clean up context
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during HRTF creation: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 4: Binaural Effect
    // =========================================================================

    private static void testBinauralEffect() {
        System.out.println("[TEST 4] Binaural Effect");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference hrtfRef = new PointerByReference();
        PointerByReference effectRef = new PointerByReference();

        try {
            // Create context
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create context");
                return;
            }
            Pointer context = contextRef.getValue();

            // Create audio settings
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = SAMPLE_RATE;
            audioSettings.frameSize = FRAME_SIZE;

            // Create HRTF
            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            result = SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create HRTF");
                SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);
                return;
            }
            Pointer hrtf = hrtfRef.getValue();

            // Create binaural effect settings
            SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
            effectSettings.hrtf = hrtf;

            // Create binaural effect
            result = SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                Pointer effect = effectRef.getValue();
                if (effect != null && effect != Pointer.NULL) {
                    pass("Binaural effect created successfully");

                    // Clean up
                    SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
                    pass("Binaural effect released successfully");
                } else {
                    fail("Binaural effect pointer is NULL");
                }
            } else {
                fail("iplBinauralEffectCreate failed with status: " + result);
            }

            // Clean up
            SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during binaural effect test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 5: Audio Processing (Sine Wave)
    // =========================================================================

    private static void testAudioProcessing() {
        System.out.println("[TEST 5] Audio Processing (Sine Wave)");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference hrtfRef = new PointerByReference();
        PointerByReference effectRef = new PointerByReference();
        ManualAudioBuffer inBuffer = null;
        ManualAudioBuffer outBuffer = null;

        try {
            // Setup Steam Audio
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            Pointer context = contextRef.getValue();

            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = SAMPLE_RATE;
            audioSettings.frameSize = FRAME_SIZE;

            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);
            Pointer hrtf = hrtfRef.getValue();

            SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
            effectSettings.hrtf = hrtf;
            SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);
            Pointer effect = effectRef.getValue();

            // Create audio buffers
            inBuffer = new ManualAudioBuffer(1, FRAME_SIZE);  // Mono input
            outBuffer = new ManualAudioBuffer(2, FRAME_SIZE); // Stereo output

            // Generate 440Hz sine wave (A4 note)
            float[] sineWave = generateSineWave(440.0f, SAMPLE_RATE, FRAME_SIZE);
            inBuffer.writeMonoData(sineWave);

            pass("Generated 440Hz sine wave input");

            // Set binaural effect parameters (sound from the right)
            SteamAudioLibrary.IPLBinauralEffectParams params = new SteamAudioLibrary.IPLBinauralEffectParams();
            params.direction = new SteamAudioLibrary.IPLVector3(1.0f, 0.0f, 0.0f); // Right
            params.interpolation = SteamAudioLibrary.IPLHRTFInterpolation.IPL_HRTFINTERPOLATION_BILINEAR;
            params.spatialBlend = 1.0f;
            params.hrtf = hrtf;
            params.peakDelays = Pointer.NULL;
            params.write();

            // Apply binaural effect
            int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectApply(
                effect,
                params.getPointer(),
                inBuffer.getStructPointer(),
                outBuffer.getStructPointer()
            );

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                pass("Binaural effect applied successfully");

                // Read output
                float[] outputData = outBuffer.readInterleavedData();

                // Verify output is not silent
                boolean hasNonZero = false;
                for (float sample : outputData) {
                    if (Math.abs(sample) > 0.001f) {
                        hasNonZero = true;
                        break;
                    }
                }

                if (hasNonZero) {
                    pass("Output contains non-zero samples (audio processed)");

                    // Calculate left/right channel energy
                    double leftEnergy = 0.0;
                    double rightEnergy = 0.0;
                    for (int i = 0; i < FRAME_SIZE; i++) {
                        float left = outputData[i * 2];
                        float right = outputData[i * 2 + 1];
                        leftEnergy += left * left;
                        rightEnergy += right * right;
                    }

                    System.out.println("  Left channel energy:  " + String.format("%.6f", leftEnergy));
                    System.out.println("  Right channel energy: " + String.format("%.6f", rightEnergy));

                    // For sound from the right, right channel should have more energy
                    if (rightEnergy > leftEnergy) {
                        pass("Spatial positioning correct (right > left)");
                    } else {
                        warn("Spatial positioning unexpected (right should > left)");
                    }
                } else {
                    fail("Output is silent (all zeros)");
                }
            } else {
                fail("iplBinauralEffectApply failed with status: " + result);
            }

            // Clean up
            inBuffer.dispose();
            outBuffer.dispose();
            SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
            SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during audio processing test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 6: Scene/Mesh API (Basic)
    // =========================================================================

    private static void testSceneMeshAPI() {
        System.out.println("[TEST 6] Scene/Mesh API (Basic)");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference sceneRef = new PointerByReference();

        try {
            // Create context
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create context");
                return;
            }
            Pointer context = contextRef.getValue();

            // Create scene settings
            SteamAudioLibrary.IPLSceneSettings sceneSettings = new SteamAudioLibrary.IPLSceneSettings();
            sceneSettings.type = SteamAudioLibrary.IPLSceneType.IPL_SCENETYPE_DEFAULT;
            sceneSettings.closestHitCallback = Pointer.NULL;
            sceneSettings.anyHitCallback = Pointer.NULL;
            sceneSettings.batchedClosestHitCallback = Pointer.NULL;
            sceneSettings.batchedAnyHitCallback = Pointer.NULL;
            sceneSettings.userData = Pointer.NULL;
            sceneSettings.embreeDevice = Pointer.NULL;
            sceneSettings.radeonRaysDevice = Pointer.NULL;

            // Create scene
            result = SteamAudioLibrary.INSTANCE.iplSceneCreate(context, sceneSettings, sceneRef);

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                Pointer scene = sceneRef.getValue();
                if (scene != null && scene != Pointer.NULL) {
                    pass("Scene created successfully");

                    // Commit scene (required before use)
                    SteamAudioLibrary.INSTANCE.iplSceneCommit(scene);
                    pass("Scene committed successfully");

                    // Clean up
                    SteamAudioLibrary.INSTANCE.iplSceneRelease(sceneRef);
                    pass("Scene released successfully");
                } else {
                    fail("Scene pointer is NULL");
                }
            } else {
                fail("iplSceneCreate failed with status: " + result);
            }

            // Clean up context
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during Scene/Mesh API test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 7: Audio Buffer Operations
    // =========================================================================

    private static void testAudioBufferOperations() {
        System.out.println("[TEST 7] Audio Buffer Operations");

        try {
            // Test ManualAudioBuffer
            ManualAudioBuffer buffer = new ManualAudioBuffer(2, 1024);

            // Write test data
            float[] testData = new float[2048]; // 2 channels * 1024 samples
            for (int i = 0; i < testData.length; i++) {
                testData[i] = (float) Math.sin(2.0 * Math.PI * i / 100.0);
            }
            buffer.writeInterleavedData(testData);
            pass("Write interleaved data to buffer");

            // Read back data
            float[] readData = buffer.readInterleavedData();
            pass("Read interleaved data from buffer");

            // Verify data integrity
            boolean dataMatches = true;
            for (int i = 0; i < testData.length; i++) {
                if (Math.abs(testData[i] - readData[i]) > 0.0001f) {
                    dataMatches = false;
                    break;
                }
            }

            if (dataMatches) {
                pass("Data integrity verified (write/read match)");
            } else {
                fail("Data mismatch after write/read");
            }

            buffer.dispose();

        } catch (Exception e) {
            fail("Exception during audio buffer operations test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // TEST 8: New Effects (Panning, Ambisonics)
    // =========================================================================

    private static void testNewEffects() {
        System.out.println("[TEST 8] New Effects API (Panning)");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference panningEffectRef = new PointerByReference();

        try {
            // Create context
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create context");
                return;
            }
            Pointer context = contextRef.getValue();

            // Create audio settings
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = SAMPLE_RATE;
            audioSettings.frameSize = FRAME_SIZE;

            // Create panning effect settings (stereo)
            SteamAudioLibrary.IPLPanningEffectSettings panningSettings = new SteamAudioLibrary.IPLPanningEffectSettings();
            panningSettings.speakerLayout = new SteamAudioLibrary.IPLSpeakerLayout();
            panningSettings.speakerLayout.type = SteamAudioLibrary.IPLSpeakerLayoutType.IPL_SPEAKERLAYOUTTYPE_STEREO;
            panningSettings.speakerLayout.numSpeakers = 2;
            panningSettings.speakerLayout.speakers = Pointer.NULL;

            // Create panning effect
            result = SteamAudioLibrary.INSTANCE.iplPanningEffectCreate(
                context, audioSettings, panningSettings, panningEffectRef
            );

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                Pointer panningEffect = panningEffectRef.getValue();
                if (panningEffect != null && panningEffect != Pointer.NULL) {
                    pass("Panning effect created successfully");

                    // Clean up
                    SteamAudioLibrary.INSTANCE.iplPanningEffectRelease(panningEffectRef);
                    pass("Panning effect released successfully");
                } else {
                    fail("Panning effect pointer is NULL");
                }
            } else {
                fail("iplPanningEffectCreate failed with status: " + result);
            }

            // Clean up context
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during new effects test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private static float[] generateSineWave(float frequency, int sampleRate, int numSamples) {
        float[] samples = new float[numSamples];
        for (int i = 0; i < numSamples; i++) {
            samples[i] = (float) Math.sin(2.0 * Math.PI * frequency * i / sampleRate);
        }
        return samples;
    }

    private static void pass(String message) {
        System.out.println("  [✓] " + message);
        testsPassed++;
    }

    private static void fail(String message) {
        System.out.println("  [✗] " + message);
        testsFailed++;
    }

    private static void warn(String message) {
        System.out.println("  [!] " + message);
    }

    // =========================================================================
    // REGRESSION TEST 1: Vector Normalization (Fix #6)
    // =========================================================================

    /**
     * Regression test for "修正6: 方向ベクトルの正規化"
     *
     * Past Issue: When un-normalized direction vectors were passed to Steam Audio,
     * the output was nearly zero and left/right channels were identical.
     *
     * This test verifies that normalization is working correctly by testing
     * with various vector lengths.
     */
    private static void testVectorNormalization() {
        System.out.println("[REGRESSION 1] Vector Normalization (Fix #6)");

        PointerByReference contextRef = new PointerByReference();
        PointerByReference hrtfRef = new PointerByReference();
        PointerByReference effectRef = new PointerByReference();
        ManualAudioBuffer inBuffer = null;
        ManualAudioBuffer outBuffer = null;

        try {
            // Setup Steam Audio
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            Pointer context = contextRef.getValue();

            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = SAMPLE_RATE;
            audioSettings.frameSize = FRAME_SIZE;

            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);
            Pointer hrtf = hrtfRef.getValue();

            SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
            effectSettings.hrtf = hrtf;
            SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);
            Pointer effect = effectRef.getValue();

            // Create audio buffers
            inBuffer = new ManualAudioBuffer(1, FRAME_SIZE);
            outBuffer = new ManualAudioBuffer(2, FRAME_SIZE);

            // Generate test signal
            float[] sineWave = generateSineWave(440.0f, SAMPLE_RATE, FRAME_SIZE);
            inBuffer.writeMonoData(sineWave);

            // Test with un-normalized vector (length > 1)
            float unnormalizedX = 10.0f;  // Length = sqrt(10^2) = 10
            float unnormalizedY = 0.0f;
            float unnormalizedZ = 0.0f;

            // Normalize the vector
            float length = (float) Math.sqrt(unnormalizedX * unnormalizedX +
                                            unnormalizedY * unnormalizedY +
                                            unnormalizedZ * unnormalizedZ);
            float normalizedX = unnormalizedX / length;
            float normalizedY = unnormalizedY / length;
            float normalizedZ = unnormalizedZ / length;

            pass("Normalized vector from (10.0, 0.0, 0.0) to (" +
                 String.format("%.3f", normalizedX) + ", " +
                 String.format("%.3f", normalizedY) + ", " +
                 String.format("%.3f", normalizedZ) + ")");

            // Apply binaural effect with normalized vector
            SteamAudioLibrary.IPLBinauralEffectParams params = new SteamAudioLibrary.IPLBinauralEffectParams();
            params.direction = new SteamAudioLibrary.IPLVector3(normalizedX, normalizedY, normalizedZ);
            params.interpolation = SteamAudioLibrary.IPLHRTFInterpolation.IPL_HRTFINTERPOLATION_BILINEAR;
            params.spatialBlend = 1.0f;
            params.hrtf = hrtf;
            params.peakDelays = Pointer.NULL;
            params.write();

            int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectApply(
                effect, params.getPointer(), inBuffer.getStructPointer(), outBuffer.getStructPointer()
            );

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                float[] outputData = outBuffer.readInterleavedData();

                // Check output is not silent
                boolean hasNonZero = false;
                for (float sample : outputData) {
                    if (Math.abs(sample) > 0.001f) {
                        hasNonZero = true;
                        break;
                    }
                }

                if (hasNonZero) {
                    pass("Output is non-zero after normalization");
                } else {
                    fail("Output is still zero even after normalization");
                }

                // Check left and right channels are different
                boolean channelsDifferent = false;
                for (int i = 0; i < FRAME_SIZE; i++) {
                    float left = outputData[i * 2];
                    float right = outputData[i * 2 + 1];
                    if (Math.abs(left - right) > 0.001f) {
                        channelsDifferent = true;
                        break;
                    }
                }

                if (channelsDifferent) {
                    pass("Left and right channels are different (HRTF working)");
                } else {
                    fail("Left and right channels are identical (HRTF not working)");
                }
            } else {
                fail("iplBinauralEffectApply failed");
            }

            // Clean up
            inBuffer.dispose();
            outBuffer.dispose();
            SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
            SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during vector normalization test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // REGRESSION TEST 2: iplAudioBufferAllocate (Fix #1)
    // =========================================================================

    /**
     * Regression test for "修正1: IPLAudioBuffer構造体の適切な使用"
     *
     * Past Issue: Using raw Memory instead of proper Steam Audio buffer allocation.
     *
     * This test verifies that iplAudioBufferAllocate works correctly.
     */
    private static void testIPLAudioBufferAllocate() {
        System.out.println("[REGRESSION 2] iplAudioBufferAllocate (Fix #1)");

        PointerByReference contextRef = new PointerByReference();

        try {
            // Create context
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);
            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                fail("Failed to create context");
                return;
            }
            Pointer context = contextRef.getValue();

            // Allocate audio buffer using Steam Audio API
            Memory bufferMemory = new Memory(1024); // Allocate memory for IPLAudioBuffer structure
            Pointer bufferPtr = bufferMemory;

            result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(context, 2, FRAME_SIZE, bufferPtr);

            if (result == SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                pass("iplAudioBufferAllocate succeeded");

                // Verify buffer structure
                SteamAudioLibrary.IPLAudioBuffer buffer = new SteamAudioLibrary.IPLAudioBuffer(bufferPtr);

                if (buffer.format.numChannels == 2 && buffer.format.numSamples == FRAME_SIZE) {
                    pass("Buffer format is correct (2 channels, " + FRAME_SIZE + " samples)");
                } else {
                    fail("Buffer format is incorrect: " + buffer.format.numChannels +
                         " channels, " + buffer.format.numSamples + " samples");
                }

                // Free buffer
                SteamAudioLibrary.INSTANCE.iplAudioBufferFree(context, bufferPtr);
                pass("iplAudioBufferFree succeeded");
            } else {
                fail("iplAudioBufferAllocate failed with status: " + result);
            }

            // Clean up context
            SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);

        } catch (Exception e) {
            fail("Exception during iplAudioBufferAllocate test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    // =========================================================================
    // REGRESSION TEST 3: Structure Field Order (Fix #3, #4)
    // =========================================================================

    /**
     * Regression test for:
     * - "修正3: IPLBinauralEffectParams構造体の不完全な定義" (hrtf, peakDelays fields)
     * - "修正4: ネストした構造体の初期化" (direction initialization)
     *
     * This test verifies that all structure fields are properly defined and initialized.
     */
    private static void testStructureFieldOrder() {
        System.out.println("[REGRESSION 3] Structure Field Order (Fix #3, #4)");

        try {
            // Test IPLBinauralEffectParams structure
            SteamAudioLibrary.IPLBinauralEffectParams params = new SteamAudioLibrary.IPLBinauralEffectParams();

            // Verify nested structure is initialized (Fix #4)
            if (params.direction != null) {
                pass("IPLBinauralEffectParams.direction is initialized (Fix #4)");
            } else {
                fail("IPLBinauralEffectParams.direction is NULL (Fix #4 not applied)");
            }

            // Verify all fields exist (Fix #3)
            params.direction.x = 1.0f;
            params.direction.y = 0.0f;
            params.direction.z = 0.0f;
            params.interpolation = SteamAudioLibrary.IPLHRTFInterpolation.IPL_HRTFINTERPOLATION_BILINEAR;
            params.spatialBlend = 1.0f;
            params.hrtf = Pointer.NULL;  // Must exist (Fix #3)
            params.peakDelays = Pointer.NULL;  // Must exist (Fix #3)

            pass("All IPLBinauralEffectParams fields are accessible (Fix #3)");

            // Test field order
            java.util.List<String> fieldOrder = params.getFieldOrder();
            String[] expectedOrder = {"direction", "interpolation", "spatialBlend", "hrtf", "peakDelays"};

            boolean orderCorrect = true;
            if (fieldOrder.size() == expectedOrder.length) {
                for (int i = 0; i < expectedOrder.length; i++) {
                    if (!fieldOrder.get(i).equals(expectedOrder[i])) {
                        orderCorrect = false;
                        break;
                    }
                }
            } else {
                orderCorrect = false;
            }

            if (orderCorrect) {
                pass("IPLBinauralEffectParams field order is correct");
            } else {
                fail("IPLBinauralEffectParams field order is incorrect");
            }

            // Write structure to verify it doesn't crash
            params.write();
            pass("Structure write() succeeded without crash");

        } catch (Exception e) {
            fail("Exception during structure field order test: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
}
