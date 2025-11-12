package jp.houlab.mochidsuki.advancedvc.network.packet;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.common.VoiceMode;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 音声データパケット（UDP経由）
 */
public class VoicePacket {
    private final byte packetType;           // パケットタイプ
    private final UUID senderId;             // 送信者UUID
    private final short sequenceNumber;      // シーケンス番号
    private final VoiceMode mode;            // 音声モード
    private final VolumeLevel volumeLevel;   // 声量レベル
    private final byte[] audioData;          // Opusエンコード済み音声データ

    // デバイスモード用
    private final int frequency;             // ウォーキートーキー周波数（デバイスモードのみ）

    // シミュレーションモード用（スピーカーブロック使用時）
    private final boolean isFromSpeaker;     // スピーカーから発信されたか
    private final double speakerX;           // スピーカー座標X
    private final double speakerY;           // スピーカー座標Y
    private final double speakerZ;           // スピーカー座標Z

    private VoicePacket(Builder builder) {
        this.packetType = builder.packetType;
        this.senderId = builder.senderId;
        this.sequenceNumber = builder.sequenceNumber;
        this.mode = builder.mode;
        this.volumeLevel = builder.volumeLevel;
        this.audioData = builder.audioData;
        this.frequency = builder.frequency;
        this.isFromSpeaker = builder.isFromSpeaker;
        this.speakerX = builder.speakerX;
        this.speakerY = builder.speakerY;
        this.speakerZ = builder.speakerZ;
    }

    /**
     * パケットをバイト配列にシリアライズ
     */
    public byte[] serialize() {
        // パケットサイズ計算
        // packetType(1) + UUID(16) + seq(2) + mode(1) + volume(1) + freq(4) + audioLen(2) + isFromSpeaker(1) + audioData
        int baseSize = 1 + 16 + 2 + 1 + 1 + 4 + 2 + 1 + audioData.length;
        if (isFromSpeaker) {
            baseSize += 8 + 8 + 8; // 座標3つ
        }

        ByteBuffer buffer = ByteBuffer.allocate(baseSize);

        // ヘッダー
        buffer.put(packetType);
        buffer.putLong(senderId.getMostSignificantBits());
        buffer.putLong(senderId.getLeastSignificantBits());
        buffer.putShort(sequenceNumber);
        buffer.put((byte) mode.ordinal());
        buffer.put((byte) volumeLevel.ordinal());
        buffer.putInt(frequency);

        // スピーカー情報
        buffer.put((byte) (isFromSpeaker ? 1 : 0));
        if (isFromSpeaker) {
            buffer.putDouble(speakerX);
            buffer.putDouble(speakerY);
            buffer.putDouble(speakerZ);
        }

        // 音声データ
        buffer.putShort((short) audioData.length);
        buffer.put(audioData);

        return buffer.array();
    }

    /**
     * バイト配列からパケットをデシリアライズ
     */
    public static VoicePacket deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        Builder builder = new Builder();

        // ヘッダー
        builder.packetType = buffer.get();
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        builder.senderId = new UUID(mostSigBits, leastSigBits);
        builder.sequenceNumber = buffer.getShort();
        builder.mode = VoiceMode.values()[buffer.get()];
        builder.volumeLevel = VolumeLevel.values()[buffer.get()];
        builder.frequency = buffer.getInt();

        // スピーカー情報
        builder.isFromSpeaker = buffer.get() == 1;
        if (builder.isFromSpeaker) {
            builder.speakerX = buffer.getDouble();
            builder.speakerY = buffer.getDouble();
            builder.speakerZ = buffer.getDouble();
        }

        // 音声データ
        short audioDataLength = buffer.getShort();
        builder.audioData = new byte[audioDataLength];
        buffer.get(builder.audioData);

        return builder.build();
    }

    // Getters
    public byte getPacketType() { return packetType; }
    public UUID getSenderId() { return senderId; }
    public short getSequenceNumber() { return sequenceNumber; }
    public VoiceMode getMode() { return mode; }
    public VolumeLevel getVolumeLevel() { return volumeLevel; }
    public byte[] getAudioData() { return audioData; }
    public int getFrequency() { return frequency; }
    public boolean isFromSpeaker() { return isFromSpeaker; }
    public double getSpeakerX() { return speakerX; }
    public double getSpeakerY() { return speakerY; }
    public double getSpeakerZ() { return speakerZ; }

    // Builder Pattern
    public static class Builder {
        private byte packetType = AudioConstants.PACKET_TYPE_SIMULATION;
        private UUID senderId;
        private short sequenceNumber;
        private VoiceMode mode = VoiceMode.SIMULATION;
        private VolumeLevel volumeLevel = VolumeLevel.NORMAL;
        private byte[] audioData;
        private int frequency = 0;
        private boolean isFromSpeaker = false;
        private double speakerX = 0;
        private double speakerY = 0;
        private double speakerZ = 0;

        public Builder packetType(byte packetType) {
            this.packetType = packetType;
            return this;
        }

        public Builder senderId(UUID senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder sequenceNumber(short sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder mode(VoiceMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder volumeLevel(VolumeLevel volumeLevel) {
            this.volumeLevel = volumeLevel;
            return this;
        }

        public Builder audioData(byte[] audioData) {
            this.audioData = audioData;
            return this;
        }

        public Builder frequency(int frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder speakerPosition(double x, double y, double z) {
            this.isFromSpeaker = true;
            this.speakerX = x;
            this.speakerY = y;
            this.speakerZ = z;
            return this;
        }

        public VoicePacket build() {
            return new VoicePacket(this);
        }
    }
}
