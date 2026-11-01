package org.example.ungdungchat.client.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AudioService {
    private static final Logger LOGGER = Logger.getLogger(AudioService.class.getName());

    public static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000.0f, // 16kHz sample rate
            16,       // 16-bit
            1,        // mono
            2,        // frame size
            16000.0f, // frame rate
            false     // little endian
    );

    private TargetDataLine targetLine;
    private SourceDataLine sourceLine;
    private final AtomicBoolean isRecordingVoiceMessage = new AtomicBoolean(false);
    private final AtomicBoolean isCallStreaming = new AtomicBoolean(false);

    private ByteArrayOutputStream voiceMessageBuffer;

    public AudioService() {
        initSpeaker();
    }

    private void initSpeaker() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            if (AudioSystem.isLineSupported(info)) {
                sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceLine.open(FORMAT);
                sourceLine.start();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể khởi tạo loa: " + e.getMessage());
        }
    }

    // =========================================================================
    // Voice Message Recording & Saving to WAV
    // =========================================================================

    public boolean startVoiceRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warning("Microphone not supported");
                return false;
            }
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(FORMAT);
            targetLine.start();

            isRecordingVoiceMessage.set(true);
            voiceMessageBuffer = new ByteArrayOutputStream();

            Thread recordThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRecordingVoiceMessage.get()) {
                    int read = targetLine.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        voiceMessageBuffer.write(buffer, 0, read);
                    }
                }
            }, "voice-record-thread");
            recordThread.setDaemon(true);
            recordThread.start();
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi bắt đầu ghi âm: " + e.getMessage());
            return false;
        }
    }

    public File stopVoiceRecording() {
        isRecordingVoiceMessage.set(false);
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
            targetLine = null;
        }

        if (voiceMessageBuffer == null || voiceMessageBuffer.size() == 0) {
            return null;
        }

        try {
            byte[] audioBytes = voiceMessageBuffer.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream ais = new AudioInputStream(bais, FORMAT, audioBytes.length / FORMAT.getFrameSize());

            File wavFile = File.createTempFile("voice_", ".wav");
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
            return wavFile;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Lỗi lưu file ghi âm WAV: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Playback of WAV or Audio bytes
    // =========================================================================

    public void playWavFile(File file) {
        new Thread(() -> {
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Lỗi phát âm thanh: " + e.getMessage());
            }
        }).start();
    }

    public void playPcmChunk(byte[] audioBytes) {
        if (sourceLine != null && sourceLine.isOpen() && audioBytes != null) {
            sourceLine.write(audioBytes, 0, audioBytes.length);
        }
    }

    // =========================================================================
    // Real-Time Voice Call Streaming
    // =========================================================================

    public void startCallAudioStreaming(Consumer<byte[]> onAudioCaptured) {
        if (isCallStreaming.get()) return;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            TargetDataLine callMic = (TargetDataLine) AudioSystem.getLine(info);
            callMic.open(FORMAT);
            callMic.start();
            isCallStreaming.set(true);

            Thread streamThread = new Thread(() -> {
                byte[] buffer = new byte[640]; // 20ms frames at 16kHz mono 16-bit
                while (isCallStreaming.get()) {
                    int read = callMic.read(buffer, 0, buffer.length);
                    if (read > 0 && onAudioCaptured != null) {
                        byte[] chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                        onAudioCaptured.accept(chunk);
                    }
                }
                callMic.stop();
                callMic.close();
            }, "call-stream-thread");
            streamThread.setDaemon(true);
            streamThread.start();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi stream mic cuộc gọi: " + e.getMessage());
        }
    }

    public void stopCallAudioStreaming() {
        isCallStreaming.set(false);
    }

    public void close() {
        stopCallAudioStreaming();
        if (targetLine != null) {
            targetLine.close();
        }
        if (sourceLine != null) {
            sourceLine.close();
        }
    }
}
