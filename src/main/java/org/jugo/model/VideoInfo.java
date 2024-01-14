package org.jugo.model;

public class VideoInfo {
    private final int width;
    private final int height;
    private final double frameRate;

    public VideoInfo(int width, int height, double frameRate) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getFrameRate() {
        return frameRate;
    }
}

