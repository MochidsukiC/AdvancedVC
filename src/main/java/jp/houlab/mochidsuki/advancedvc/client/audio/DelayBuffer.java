package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 音声サンプルの遅延バッファ
 * 初期反射音の遅延時間を実現するために使用
 */
public class DelayBuffer {
    private final ConcurrentLinkedQueue<short[]> buffer;
    private final int delayFrames;  // 遅延フレーム数

    /**
     * コンストラクタ
     * @param delayMs 遅延時間（ミリ秒）
     */
    public DelayBuffer(float delayMs) {
        this.buffer = new ConcurrentLinkedQueue<>();

        // ミリ秒をフレーム数に変換
        // AudioConstants.SAMPLE_RATE = 48000 Hz
        // AudioConstants.FRAME_SIZE = 960サンプル
        // 1フレーム = 960 / 48000 = 20ms
        this.delayFrames = (int) Math.ceil(delayMs / 20.0f);
    }

    /**
     * サンプルを追加
     * @param samples 音声サンプル
     */
    public void addSamples(short[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }

        // バッファに追加
        buffer.offer(samples.clone());  // コピーして追加
    }

    /**
     * 遅延されたサンプルを取得
     * @return 遅延されたサンプル、まだ遅延中の場合はnull
     */
    public short[] getDelayedSamples() {
        // delayFrames分のバッファが溜まっていれば取り出し
        if (buffer.size() >= delayFrames) {
            return buffer.poll();
        }
        return null;  // まだ遅延中
    }

    /**
     * バッファをクリア
     */
    public void clear() {
        buffer.clear();
    }

    /**
     * 現在のバッファサイズを取得
     * @return バッファ内のフレーム数
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * 遅延フレーム数を取得
     * @return 設定された遅延フレーム数
     */
    public int getDelayFrames() {
        return delayFrames;
    }
}
