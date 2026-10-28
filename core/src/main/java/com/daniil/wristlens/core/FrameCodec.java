package com.daniil.wristlens.core;

import java.io.*;

/** Bounded, versioned, network-order frame. Coordinates always refer to raw JPEG pixels. */
public final class FrameCodec {
    public static final int MAGIC = 0x574c3031;
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_SIDE = 2048;
    public record Frame(long sequence, int width, int height, int rotation, boolean mirror, byte[] jpeg) {}
    public static void write(DataOutputStream out, Frame f) throws IOException {
        validate(f.width, f.height, f.rotation, f.jpeg.length);
        out.writeInt(MAGIC); out.writeLong(f.sequence);
        out.writeInt(f.width); out.writeInt(f.height); out.writeInt(f.rotation);
        out.writeBoolean(f.mirror); out.writeInt(f.jpeg.length); out.write(f.jpeg); out.flush();
    }
    public static Frame read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Unsupported frame protocol");
        long seq = in.readLong();
        int w = in.readInt(), h = in.readInt(), rotation = in.readInt();
        boolean mirror = in.readBoolean(); int size = in.readInt();
        validate(w, h, rotation, size);
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return new Frame(seq,w,h,rotation,mirror,bytes);
    }
    private static void validate(int w,int h,int r,int size) throws IOException {
        if(w<1 || h<1 || w>MAX_SIDE || h>MAX_SIDE || size<1 || size>MAX_BYTES ||
           (r!=0 && r!=90 && r!=180 && r!=270)) throw new IOException("Invalid frame bounds");
    }
}
