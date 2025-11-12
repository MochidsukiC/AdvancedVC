package jp.houlab.mochidsuki.advancedvc.client.audio.vgp;

/**
 * 音響経路の処理結果
 *
 * VGPで発見された経路を、オーディオ処理に必要なパラメータに変換した結果を保持します。
 */
public class AcousticPathResult {

    // 基本情報
    public final float distance;                // 経路の総距離（m）
    public final float straightLineDistance;    // 直線距離（m）
    public final float travelTime;              // 音の到達時間（秒）

    // 回折（Diffraction）
    public final boolean isDiffracted;          // 回折が発生したか
    public final float diffractionAmount;       // 回折量（m）
    public final float diffractionAttenuation;  // 回折による減衰（dB）

    // 吸収（Absorption）
    public final float absorptionCoeff;         // 吸収係数（0.0-1.0）
    public final float absorptionAttenuation;   // 吸収による減衰（dB）

    // フィルタリング
    public final float lowpassCutoff;           // ローパスフィルタのカットオフ周波数（Hz）
    public final float filterGain;              // フィルタゲイン（0.0-1.0）

    // 総合減衰
    public final float totalAttenuation;        // 総合減衰（dB）
    public final float totalGain;               // 総合ゲイン（0.0-1.0）

    /**
     * コンストラクタ
     */
    public AcousticPathResult(
            float distance,
            float straightLineDistance,
            float travelTime,
            boolean isDiffracted,
            float diffractionAmount,
            float diffractionAttenuation,
            float absorptionCoeff,
            float absorptionAttenuation,
            float lowpassCutoff,
            float filterGain,
            float totalAttenuation,
            float totalGain
    ) {
        this.distance = distance;
        this.straightLineDistance = straightLineDistance;
        this.travelTime = travelTime;
        this.isDiffracted = isDiffracted;
        this.diffractionAmount = diffractionAmount;
        this.diffractionAttenuation = diffractionAttenuation;
        this.absorptionCoeff = absorptionCoeff;
        this.absorptionAttenuation = absorptionAttenuation;
        this.lowpassCutoff = lowpassCutoff;
        this.filterGain = filterGain;
        this.totalAttenuation = totalAttenuation;
        this.totalGain = totalGain;
    }

    /**
     * VGPPathfinder.AcousticPathから音響処理結果を生成
     */
    public static AcousticPathResult fromPath(VGPPathfinder.AcousticPath path) {
        // 音速（m/s）
        final float SOUND_SPEED = 343.0f;

        // 基本情報
        float distance = path.totalDistance;
        float straightLineDistance = path.straightLineDistance;
        float travelTime = distance / SOUND_SPEED;

        // 回折による減衰
        // 回折量が大きいほど高周波が減衰し、音がこもる
        boolean isDiffracted = path.isDiffracted;
        float diffractionAmount = path.diffractionAmount;
        float diffractionAttenuation = 0.0f;
        float diffractionLowpassCutoff = 20000.0f;  // 初期値: 全周波数通過

        if (isDiffracted) {
            // 回折量に応じて減衰を計算
            // 経路長差1mあたり約3dB減衰（経験的モデル）
            diffractionAttenuation = diffractionAmount * 3.0f;

            // 高周波のカットオフ周波数を下げる
            // 回折量が大きいほど低周波のみ通過
            diffractionLowpassCutoff = Math.max(500.0f, 5000.0f / (1.0f + diffractionAmount));
        }

        // 吸収による減衰
        float absorptionCoeff = path.averageAbsorption;
        // 吸収係数に応じた減衰（0.0 = 減衰なし、1.0 = 完全減衰）
        float absorptionAttenuation = absorptionCoeff * 20.0f;  // 最大20dB減衰

        // フィルタリングパラメータ
        float lowpassCutoff = diffractionLowpassCutoff;
        float filterGain = 1.0f - absorptionCoeff * 0.5f;  // 吸収係数に応じてゲイン低下

        // 距離減衰（逆二乗則の近似）
        float distanceAttenuation = 20.0f * (float) Math.log10(1.0 + distance / 5.0);

        // 総合減衰
        float totalAttenuation = distanceAttenuation + diffractionAttenuation + absorptionAttenuation;
        // dBからリニアゲインに変換
        float totalGain = (float) Math.pow(10.0, -totalAttenuation / 20.0);
        // ゲインを0.0-1.0にクランプ
        totalGain = Math.max(0.0f, Math.min(1.0f, totalGain));

        return new AcousticPathResult(
                distance,
                straightLineDistance,
                travelTime,
                isDiffracted,
                diffractionAmount,
                diffractionAttenuation,
                absorptionCoeff,
                absorptionAttenuation,
                lowpassCutoff,
                filterGain,
                totalAttenuation,
                totalGain
        );
    }

    @Override
    public String toString() {
        return String.format(
                "AcousticPathResult{distance=%.2fm, time=%.3fs, diffracted=%s, diffAmount=%.2fm, " +
                "absorption=%.2f, lowpass=%.0fHz, totalGain=%.2f}",
                distance, travelTime, isDiffracted, diffractionAmount,
                absorptionCoeff, lowpassCutoff, totalGain
        );
    }
}
