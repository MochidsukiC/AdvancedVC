package jp.houlab.mochidsuki.advancedvc.client.audio;

/**
 * OpenAL EFX Reverbの設定
 * 環境に応じたリバーブパラメータを管理
 */
public class EFXReverbSettings {

    // リバーブの基本パラメータ
    public float density = 1.0f;          // 密度（0.0～1.0）
    public float diffusion = 1.0f;        // 拡散（0.0～1.0）
    public float gain = 0.32f;            // 全体ゲイン
    public float gainHF = 0.89f;          // 高周波ゲイン
    public float gainLF = 1.0f;           // 低周波ゲイン

    // 減衰時間
    public float decayTime = 1.49f;       // 残響時間（秒）
    public float decayHFRatio = 0.83f;    // 高周波減衰比
    public float decayLFRatio = 1.0f;     // 低周波減衰比

    // 初期反射（Early Reflections）
    public float reflectionsGain = 0.05f;
    public float reflectionsDelay = 0.007f;

    // 後部残響（Late Reverb）
    public float lateReverbGain = 1.26f;
    public float lateReverbDelay = 0.011f;

    // 空気吸収
    public float airAbsorptionGainHF = 0.994f;
    public float airAbsorptionHF = 0.1f;      // 空気吸収係数（0.0～10.0）

    // 距離減衰
    public float roomRolloffFactor = 1.0f;    // Room Rolloff Factor（0.0～10.0）

    // 周波数基準
    public float hfReference = 5000.0f;       // 高周波基準周波数（Hz）
    public float lfReference = 250.0f;        // 低周波基準周波数（Hz）

    // 空間サイズ
    public float roomSize = 0.5f;             // 空間サイズ感（0.0～1.0）

    /**
     * プリセット: 小さな部屋
     */
    public static EFXReverbSettings smallRoom() {
        EFXReverbSettings settings = new EFXReverbSettings();
        settings.decayTime = 0.5f;
        settings.density = 1.0f;
        settings.diffusion = 1.0f;
        settings.reflectionsGain = 0.1f;
        settings.reflectionsDelay = 0.005f;
        settings.lateReverbGain = 0.8f;
        settings.lateReverbDelay = 0.008f;
        return settings;
    }

    /**
     * プリセット: 中程度の部屋
     */
    public static EFXReverbSettings mediumRoom() {
        EFXReverbSettings settings = new EFXReverbSettings();
        settings.decayTime = 1.5f;
        settings.density = 1.0f;
        settings.diffusion = 1.0f;
        settings.reflectionsGain = 0.05f;
        settings.reflectionsDelay = 0.007f;
        settings.lateReverbGain = 1.26f;
        settings.lateReverbDelay = 0.011f;
        return settings;
    }

    /**
     * プリセット: 大きな部屋
     */
    public static EFXReverbSettings largeRoom() {
        EFXReverbSettings settings = new EFXReverbSettings();
        settings.decayTime = 2.5f;
        settings.density = 1.0f;
        settings.diffusion = 1.0f;
        settings.reflectionsGain = 0.03f;
        settings.reflectionsDelay = 0.01f;
        settings.lateReverbGain = 1.5f;
        settings.lateReverbDelay = 0.015f;
        return settings;
    }

    /**
     * プリセット: 洞窟
     */
    public static EFXReverbSettings cave() {
        EFXReverbSettings settings = new EFXReverbSettings();
        settings.decayTime = 4.0f;
        settings.density = 1.0f;
        settings.diffusion = 0.7f;
        settings.reflectionsGain = 0.02f;
        settings.reflectionsDelay = 0.015f;
        settings.lateReverbGain = 2.0f;
        settings.lateReverbDelay = 0.03f;
        return settings;
    }

    /**
     * プリセット: 屋外（ほぼDRY）
     */
    public static EFXReverbSettings outdoor() {
        EFXReverbSettings settings = new EFXReverbSettings();
        settings.decayTime = 0.1f;
        settings.density = 0.3f;
        settings.diffusion = 0.2f;
        settings.gain = 0.05f;
        settings.reflectionsGain = 0.01f;
        settings.reflectionsDelay = 0.002f;
        settings.lateReverbGain = 0.02f; // WET=2%（ほぼDRY）
        settings.lateReverbDelay = 0.005f;
        return settings;
    }

    /**
     * RT60（残響時間）から設定を作成
     * @param rt60 残響時間（秒）
     * @param volume 空間の体積（m³）
     * @return リバーブ設定
     */
    public static EFXReverbSettings fromRT60(float rt60, float volume) {
        EFXReverbSettings settings = new EFXReverbSettings();

        // RT60をDecay Timeに変換
        settings.decayTime = Math.max(0.1f, Math.min(rt60, 20.0f));

        // 体積に応じた調整
        if (volume < 50f) {
            // 小空間
            settings.density = 1.0f;
            settings.diffusion = 1.0f;
            settings.reflectionsDelay = 0.005f;
            settings.lateReverbDelay = 0.008f;
        } else if (volume < 200f) {
            // 中空間
            settings.density = 1.0f;
            settings.diffusion = 1.0f;
            settings.reflectionsDelay = 0.01f;
            settings.lateReverbDelay = 0.015f;
        } else {
            // 大空間
            settings.density = 1.0f;
            settings.diffusion = 0.9f;
            settings.reflectionsDelay = 0.02f;
            settings.lateReverbDelay = 0.03f;
        }

        // ゲイン初期値（後でEnvironmentAnalyzerで吸音係数に応じて上書き）
        // RT60が長くても、吸音材があれば低ゲインになる
        settings.lateReverbGain = 0.8f; // デフォルト値（後で調整される）

        return settings;
    }

    /**
     * 2つの設定を線形補間
     * @param a 設定A
     * @param b 設定B
     * @param t 補間係数（0.0～1.0）
     * @return 補間された設定
     */
    public static EFXReverbSettings lerp(EFXReverbSettings a, EFXReverbSettings b, float t) {
        EFXReverbSettings result = new EFXReverbSettings();

        result.density = lerp(a.density, b.density, t);
        result.diffusion = lerp(a.diffusion, b.diffusion, t);
        result.gain = lerp(a.gain, b.gain, t);
        result.gainHF = lerp(a.gainHF, b.gainHF, t);
        result.gainLF = lerp(a.gainLF, b.gainLF, t);

        result.decayTime = lerp(a.decayTime, b.decayTime, t);
        result.decayHFRatio = lerp(a.decayHFRatio, b.decayHFRatio, t);
        result.decayLFRatio = lerp(a.decayLFRatio, b.decayLFRatio, t);

        result.reflectionsGain = lerp(a.reflectionsGain, b.reflectionsGain, t);
        result.reflectionsDelay = lerp(a.reflectionsDelay, b.reflectionsDelay, t);

        result.lateReverbGain = lerp(a.lateReverbGain, b.lateReverbGain, t);
        result.lateReverbDelay = lerp(a.lateReverbDelay, b.lateReverbDelay, t);

        result.airAbsorptionGainHF = lerp(a.airAbsorptionGainHF, b.airAbsorptionGainHF, t);
        result.airAbsorptionHF = lerp(a.airAbsorptionHF, b.airAbsorptionHF, t);
        result.roomRolloffFactor = lerp(a.roomRolloffFactor, b.roomRolloffFactor, t);
        result.hfReference = lerp(a.hfReference, b.hfReference, t);
        result.lfReference = lerp(a.lfReference, b.lfReference, t);
        result.roomSize = lerp(a.roomSize, b.roomSize, t);

        return result;
    }

    /**
     * floatの線形補間
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 設定をコピー
     */
    public EFXReverbSettings copy() {
        EFXReverbSettings copy = new EFXReverbSettings();
        copy.density = this.density;
        copy.diffusion = this.diffusion;
        copy.gain = this.gain;
        copy.gainHF = this.gainHF;
        copy.gainLF = this.gainLF;
        copy.decayTime = this.decayTime;
        copy.decayHFRatio = this.decayHFRatio;
        copy.decayLFRatio = this.decayLFRatio;
        copy.reflectionsGain = this.reflectionsGain;
        copy.reflectionsDelay = this.reflectionsDelay;
        copy.lateReverbGain = this.lateReverbGain;
        copy.lateReverbDelay = this.lateReverbDelay;
        copy.airAbsorptionGainHF = this.airAbsorptionGainHF;
        copy.airAbsorptionHF = this.airAbsorptionHF;
        copy.roomRolloffFactor = this.roomRolloffFactor;
        copy.hfReference = this.hfReference;
        copy.lfReference = this.lfReference;
        copy.roomSize = this.roomSize;
        return copy;
    }

    @Override
    public String toString() {
        return String.format(
                "EFXReverbSettings{decayTime=%.2f, density=%.2f, diffusion=%.2f}",
                decayTime, density, diffusion
        );
    }
}
