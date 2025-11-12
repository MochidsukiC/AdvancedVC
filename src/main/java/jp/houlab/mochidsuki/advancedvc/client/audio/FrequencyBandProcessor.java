package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * 周波数帯域別処理プロセッサー
 * 6バンド（250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz）に分割して個別処理
 * FFT/IFFTベースの実装
 */
public class FrequencyBandProcessor {
    // 周波数バンドの境界（Hz）
    private static final float[] BAND_FREQUENCIES = {
            250f,   // Band 0: 0-250Hz
            500f,   // Band 1: 250-500Hz
            1000f,  // Band 2: 500-1000Hz
            2000f,  // Band 3: 1000-2000Hz
            4000f,  // Band 4: 2000-4000Hz
            8000f,  // Band 5: 4000-8000Hz
            24000f  // Band 6: 8000Hz以上（ナイキスト周波数まで）
    };

    private static final int NUM_BANDS = BAND_FREQUENCIES.length - 1;

    // FFTサイズ（2のべき乗、フレームサイズ以上）
    private static final int FFT_SIZE = nextPowerOfTwo(AudioConstants.FRAME_SIZE);

    /**
     * 周波数バンドごとに減衰を適用
     * @param samples 入力サンプル（モノラル）
     * @param bandAttenuations 各バンドの減衰率（0.0=完全カット, 1.0=減衰なし）
     * @return 処理後のサンプル
     */
    public static short[] processBands(short[] samples, float[] bandAttenuations) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        if (bandAttenuations.length != NUM_BANDS) {
            throw new IllegalArgumentException("bandAttenuations must have " + NUM_BANDS + " elements");
        }

        // 入力をfloat配列に変換し、FFTサイズにゼロパディング
        float[] input = new float[FFT_SIZE];
        for (int i = 0; i < samples.length && i < FFT_SIZE; i++) {
            input[i] = samples[i];
        }

        // FFT実行
        Complex[] spectrum = fft(input);

        // 各バンドに減衰を適用
        applyBandAttenuations(spectrum, bandAttenuations);

        // IFFT実行
        float[] output = ifft(spectrum);

        // short配列に変換
        short[] result = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float sample = output[i];
            // クリッピング
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }
            result[i] = (short) sample;
        }

        return result;
    }

    /**
     * 周波数スペクトルに各バンドの減衰を適用
     */
    private static void applyBandAttenuations(Complex[] spectrum, float[] attenuations) {
        int sampleRate = AudioConstants.SAMPLE_RATE;
        int fftSize = spectrum.length;

        // 各周波数ビンに対して処理
        for (int bin = 0; bin < fftSize / 2; bin++) {
            // このビンの周波数を計算
            float frequency = (float) bin * sampleRate / fftSize;

            // どのバンドに属するか判定
            int band = getBandIndex(frequency);

            // 該当バンドの減衰を適用
            if (band >= 0 && band < attenuations.length) {
                spectrum[bin] = spectrum[bin].multiply(attenuations[band]);
                // 負の周波数側も同様に処理（実数信号の対称性）
                if (bin > 0) {
                    spectrum[fftSize - bin] = spectrum[fftSize - bin].multiply(attenuations[band]);
                }
            }
        }
    }

    /**
     * 周波数がどのバンドに属するか判定
     */
    private static int getBandIndex(float frequency) {
        for (int i = 0; i < BAND_FREQUENCIES.length - 1; i++) {
            if (frequency < BAND_FREQUENCIES[i + 1]) {
                return i;
            }
        }
        return NUM_BANDS - 1;
    }

    /**
     * 高速フーリエ変換（Cooley-Tukey FFT）
     */
    private static Complex[] fft(float[] input) {
        int n = input.length;
        if (n == 1) {
            return new Complex[]{new Complex(input[0], 0)};
        }

        // float配列をComplex配列に変換
        Complex[] x = new Complex[n];
        for (int i = 0; i < n; i++) {
            x[i] = new Complex(input[i], 0);
        }

        return fftRecursive(x);
    }

    /**
     * FFT再帰実装
     */
    private static Complex[] fftRecursive(Complex[] x) {
        int n = x.length;

        // ベースケース
        if (n == 1) {
            return new Complex[]{x[0]};
        }

        // 偶数と奇数に分割
        Complex[] even = new Complex[n / 2];
        Complex[] odd = new Complex[n / 2];
        for (int k = 0; k < n / 2; k++) {
            even[k] = x[2 * k];
            odd[k] = x[2 * k + 1];
        }

        // 再帰的にFFT
        Complex[] q = fftRecursive(even);
        Complex[] r = fftRecursive(odd);

        // 結合
        Complex[] y = new Complex[n];
        for (int k = 0; k < n / 2; k++) {
            double kth = -2 * k * Math.PI / n;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k] = q[k].add(wk.multiply(r[k]));
            y[k + n / 2] = q[k].subtract(wk.multiply(r[k]));
        }
        return y;
    }

    /**
     * 逆高速フーリエ変換（IFFT）
     */
    private static float[] ifft(Complex[] spectrum) {
        int n = spectrum.length;

        // 共役を取る
        Complex[] conjugate = new Complex[n];
        for (int i = 0; i < n; i++) {
            conjugate[i] = spectrum[i].conjugate();
        }

        // FFT適用
        Complex[] result = fftRecursive(conjugate);

        // 共役を取り、nで割る
        float[] output = new float[n];
        for (int i = 0; i < n; i++) {
            output[i] = (float) (result[i].conjugate().real / n);
        }

        return output;
    }

    /**
     * 次の2のべき乗を計算
     */
    private static int nextPowerOfTwo(int n) {
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }

    /**
     * 複素数クラス
     */
    private static class Complex {
        final double real;
        final double imag;

        Complex(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        Complex add(Complex other) {
            return new Complex(this.real + other.real, this.imag + other.imag);
        }

        Complex subtract(Complex other) {
            return new Complex(this.real - other.real, this.imag - other.imag);
        }

        Complex multiply(Complex other) {
            double r = this.real * other.real - this.imag * other.imag;
            double i = this.real * other.imag + this.imag * other.real;
            return new Complex(r, i);
        }

        Complex multiply(float scalar) {
            return new Complex(this.real * scalar, this.imag * scalar);
        }

        Complex conjugate() {
            return new Complex(this.real, -this.imag);
        }
    }

    /**
     * バンド数を取得
     */
    public static int getNumBands() {
        return NUM_BANDS;
    }

    /**
     * バンド境界周波数を取得
     */
    public static float[] getBandFrequencies() {
        return BAND_FREQUENCIES.clone();
    }
}
