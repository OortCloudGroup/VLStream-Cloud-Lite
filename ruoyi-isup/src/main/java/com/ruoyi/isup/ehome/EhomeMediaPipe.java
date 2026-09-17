package com.ruoyi.isup.ehome;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import java.io.*;
import java.util.concurrent.*;

/** Bounded PS to FLV remuxing. Native callback threads never block on the media server. */
public class EhomeMediaPipe implements AutoCloseable {
    private final PacketInput input = new PacketInput();
    private final CompletableFuture<String> ready;
    private final Thread worker;
    private volatile boolean running = true;
    public EhomeMediaPipe(String url, CompletableFuture<String> ready) {
        this.ready = ready;
        worker = new Thread(() -> run(url), "ehome-media");
        worker.setDaemon(true);
        worker.start();
    }
    public void accept(byte[] bytes) {
        if (running && !input.offer(bytes)) { ready.complete("false"); close(); }
    }
    public boolean running() { return running; }
    private void run(String url) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(input, 0)) {
            grabber.setFormat("mpeg");
            grabber.setOption("probesize", "1048576");
            grabber.setOption("analyzeduration", "1000000");
            grabber.start();
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(url, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {
                recorder.setFormat("flv");
                recorder.setOption("rw_timeout", "5000000");
                recorder.start(grabber.getFormatContext());
                AVPacket packet;
                while (running && (packet = grabber.grabPacket()) != null) {
                    recorder.recordPacket(packet);
                    ready.complete("true");
                }
            }
        } catch (Exception e) {
            // Native error text can contain signed media URLs.
            ready.complete("false");
        } finally {
            running = false;
            input.close();
            ready.complete("false");
        }
    }
    @Override public void close() {
        running = false;
        input.close();
        ready.complete("false");
        worker.interrupt();
    }
    static final class PacketInput extends InputStream {
        private final BlockingQueue<byte[]> packets = new ArrayBlockingQueue<>(64);
        private final byte[] eof = new byte[0];
        private volatile boolean closed;
        private byte[] current;
        private int position, queuedBytes;
        synchronized boolean offer(byte[] bytes) {
            if (closed) return false;
            if (bytes.length == 0) return true;
            if (queuedBytes + bytes.length > 8 * 1024 * 1024 || !packets.offer(bytes)) return false;
            queuedBytes += bytes.length;
            return true;
        }
        @Override public synchronized void close() {
            closed = true;
            packets.clear();
            queuedBytes = 0;
            packets.offer(eof);
        }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (target == null) throw new NullPointerException();
            if (offset < 0 || length < 0 || offset > target.length - length) throw new IndexOutOfBoundsException();
            if (length == 0) return 0;
            if (closed) return -1;
            if (current == null || position == current.length) {
                try { current = packets.take(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Stream interrupted"); }
                if (current == eof || closed) return -1;
                synchronized (this) { queuedBytes -= current.length; }
                position = 0;
            }
            int size = Math.min(length, current.length - position);
            System.arraycopy(current, position, target, offset, size);
            position += size;
            return size;
        }
        @Override public int read() throws IOException {
            byte[] value = new byte[1];
            return read(value, 0, 1) == -1 ? -1 : value[0] & 255;
        }
    }
}
