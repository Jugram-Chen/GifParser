package org.jugo.utils;


import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder;
import org.jugo.model.VideoInfo;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FfMpegUtils {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FfMpegUtils.class);
    private static String ffmpegExePath;
    private static FFmpeg ffmpeg;

    static {
        try {
            extractFfpmgExeFromZip(FfMpegUtils.class.getClassLoader().getResource("ffmpeg.zip").getPath());
            ffmpegExePath = FfMpegUtils.class.getClassLoader().getResource("ffmpeg.exe").getPath();
            ffmpeg = new FFmpeg(ffmpegExePath);
        } catch (IOException ioe) {
            LOGGER.warn("Can not read ffmpeg.exe in resource: {}", ioe.getMessage());
            String jarPath;
            try {
                jarPath = new File(FfMpegUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
                LOGGER.info("jarPath: {}", jarPath);
            } catch (URISyntaxException ex) {
                LOGGER.error("Can not find Jar Path: {}", ex.getMessage());
                throw new RuntimeException(ex.getMessage());
            }
            try {
                extractFfmpegExeFromJar(jarPath);
            } catch (IOException ioe2) {
                LOGGER.error("Can not extract ffmpeg folder: {}", ioe2.getMessage());
                throw new RuntimeException(ioe2.getMessage());
            }
            ffmpegExePath = Paths.get(jarPath).getParent().toString() + File.separator + "ffmpeg.exe";
            try {
                ffmpeg = new FFmpeg(ffmpegExePath);
            } catch (IOException ioe3) {
                LOGGER.error("Can not construct ffmpeg: {}", ioe3.getMessage());
                throw new RuntimeException(ioe3.getMessage());
            }
        }
    }

    public static void convertToGif(String inputPath, String outputPath, Integer width, Integer height, Double frameRate) throws IOException {
        // 创建FFmpeg命令构建器
        FFmpegOutputBuilder fFmpegOutputBuilder = new FFmpegBuilder()
                .setInput(inputPath) // 设置输入视频文件
                .overrideOutputFiles(true) // 覆盖输出文件
                .addOutput(outputPath) // 设置输出文件路径
                .setFormat("gif") // 设置输出格式为gif
                .setVideoCodec("gif") // 设置视频编码器为gif
                .setStrict(FFmpegBuilder.Strict.NORMAL);// 设置编码模式
        if (width != null && height != null) {
            fFmpegOutputBuilder.setVideoResolution(width, height);// 设置分辨率
        }
        if (frameRate != null) {
            fFmpegOutputBuilder.setVideoFrameRate(frameRate);// 设置帧率
        }
        FFmpegBuilder builder = fFmpegOutputBuilder.done();
        // 创建执行器并执行转换命令
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);
        executor.createJob(builder).run();
    }

    // 读取视频参数
    public static VideoInfo readVideoInfo(String videoPath) throws IOException {
        // 构建 ffmpeg 命令
        ProcessBuilder processBuilder = new ProcessBuilder(ffmpegExePath, "-i", videoPath);
        Process process = processBuilder.start();
        // 读取 ffmpeg 的 stderr（ffmpeg 把视频信息写在 stderr）
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("Video:")) {
                Integer width = null;
                Integer height = null;
                Double frameRate = null;
                Pattern resolutionPattern = Pattern.compile(", (\\d+)x(\\d+),");
                Pattern frameRatePattern = Pattern.compile("(\\d+(?:\\.\\d+)?) fps");
                // 查找分辨率
                Matcher resolutionMatcher = resolutionPattern.matcher(line);
                if (resolutionMatcher.find()) {
                    width = Integer.parseInt(resolutionMatcher.group(1));
                    height = Integer.parseInt(resolutionMatcher.group(2));
                    // 查找帧率
                    Matcher frameRateMatcher = frameRatePattern.matcher(line);
                    if (frameRateMatcher.find()) {
                        frameRate = Double.parseDouble(frameRateMatcher.group(1));
                        return new VideoInfo(width, height, frameRate);
                    }
                }

            }
        }
        throw new IllegalArgumentException("can not read video info");
    }

//    public static void extractFfpmgExe(String jarPath) throws IOException {
//        File jarFile = new File(jarPath);
//        String destDir = jarFile.getParent();
//        ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(jarFile.toPath()));
//        ZipEntry entry = zipIn.getNextEntry();
//        while (entry != null) {
//            if (entry.getName().equals("ffmpeg.exe")) {
//                File destFile = new File(destDir, "ffmpeg.exe");
//                try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(destFile.toPath()))) {
//                    byte[] bytesIn = new byte[4096];
//                    int read = 0;
//                    while ((read = zipIn.read(bytesIn)) != -1) {
//                        bos.write(bytesIn, 0, read);
//                    }
//                }
//                break;
//            }
//            zipIn.closeEntry();
//            entry = zipIn.getNextEntry();
//        }
//    }

    public static void extractFfpmgExeFromZip(String zipFilePath) throws IOException {
        File zipFile = new File(zipFilePath);
        String destDir = zipFile.getParent();
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zipIn.getNextEntry();

            // 遍历 ZIP 文件内的所有条目
            while (entry != null) {
                if (entry.getName().equals("ffmpeg.exe")) {
                    // 找到匹配的文件，提取它
                    File destFile = new File(destDir, entry.getName());
                    extractFile(zipIn, destFile);
                    break;
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private static void extractFile(ZipInputStream zipIn, File destFile) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile))) {
            byte[] bytesIn = new byte[4096];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    public static void extractFfmpegExeFromJar(String jarFilePath) throws IOException {
        File jarFile = new File(jarFilePath);

        String destDir = jarFile.getParent();
        try (ZipInputStream jarZipIn = new ZipInputStream(Files.newInputStream(jarFile.toPath()))) {
            ZipEntry entry = jarZipIn.getNextEntry();
            while (entry != null) {
                if (entry.getName().equals("ffmpeg.zip")) {
                    File tempZipFile = File.createTempFile("ffmpeg", ".zip");
                    try (FileOutputStream out = new FileOutputStream(tempZipFile)) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = jarZipIn.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                    }
                    try (ZipInputStream ffmpegZipIn = new ZipInputStream(Files.newInputStream(tempZipFile.toPath()))) {
                        ZipEntry ffmpegEntry = ffmpegZipIn.getNextEntry();

                        // 假设 ffmpeg.zip 内只有一个文件，直接提取它
                        if (ffmpegEntry != null && ffmpegEntry.getName().equals("ffmpeg.exe")) {
                            File destFile = new File(destDir, ffmpegEntry.getName());
                            extractFile(ffmpegZipIn, destFile);
                        }
                        ffmpegZipIn.closeEntry();
                    } finally {
                        tempZipFile.delete(); // 删除临时文件
                    }
                    break;
                }
                jarZipIn.closeEntry();
                entry = jarZipIn.getNextEntry();
            }
        }
    }
}
