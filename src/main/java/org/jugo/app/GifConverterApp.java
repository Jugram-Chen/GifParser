package org.jugo.app;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.jugo.model.VideoInfo;
import org.jugo.utils.ExceptionUtils;
import org.jugo.utils.FfMpegUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class GifConverterApp extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(GifConverterApp.class);
    private String inputFilePath;
    private String outputFilePath;
    private Double frameRate;
    private Integer width;
    private Integer height;
    private File lastInputSelectDirectory;
    private File lastOutputSelectDirectory;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // 1、创建UI组件
        // 输入文件
        Label selectVideoLabel = new Label("Select Video:");
        Button selectVideoButton = new Button("Browse");
        TextField videoPathField = new TextField();
        videoPathField.setDisable(true);
        videoPathField.setFocusTraversable(false);
        videoPathField.setPromptText("Video file path");
        // 输出位置
        Label outputPathLabel = new Label("Output Path:");
        Button outputPathButton = new Button("Browse");
        outputPathButton.setDisable(true);
        TextField outputPathField = new TextField();
        outputPathField.setDisable(true);
        outputPathField.setPromptText("Output Path");
        // 帧率
        Label frameRateLabel = new Label("Frame Rate:");
        TextField frameRateField = new TextField();
        frameRateField.setPromptText("e.g., 24");
        // 宽高
        Label widthLabel = new Label("Width:");
        TextField widthField = new TextField();
        widthField.setPromptText("Width in pixels");
        Label heightLabel = new Label("Height:");
        TextField heightField = new TextField();
        heightField.setPromptText("Height in pixels");
        // 开始按钮
        Button startButton = new Button("Convert");
        startButton.setDisable(true);
        // 2、创建布局
        HBox logoAndVersion = drawLogoAndVersion();
        HBox videoSelectionLayout = new HBox(10, selectVideoLabel, videoPathField, selectVideoButton);
        HBox frameRateLayout = new HBox(10, frameRateLabel, frameRateField);
        HBox outputPathLayout = new HBox(10, outputPathLabel, outputPathField, outputPathButton);
        HBox dimensionLayout = new HBox(10, widthLabel, widthField, heightLabel, heightField);
        VBox mainLayout = new VBox(20, logoAndVersion, videoSelectionLayout, outputPathLayout, frameRateLayout, dimensionLayout, startButton);
        // 遮罩层
        StackPane maskLayer = new StackPane();
        maskLayer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5)");  // 半透明背景
        maskLayer.setVisible(false);
        ProgressIndicator progressIndicator = new ProgressIndicator();  // 转圈的进度指示器
        progressIndicator.setMaxSize(50, 50);
        maskLayer.getChildren().add(progressIndicator);  // 将进度指示器添加到遮罩层中
        StackPane.setAlignment(progressIndicator, Pos.CENTER);  // 将进度指示器居中
        // 合并两层
        StackPane rootLayout = new StackPane();  // 创建一个新的StackPane作为最外层布局
        rootLayout.getChildren().addAll(mainLayout, maskLayer);  // 添加mainLayout和maskLayer
        // 3、行为业务监听设置
        // 选择视频文件
        mainLayout.setOnMouseClicked(event -> {
            mainLayout.requestFocus();
        });
        selectVideoButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            if (lastInputSelectDirectory != null) {
                fileChooser.setInitialDirectory(lastInputSelectDirectory);
            }
            FileChooser.ExtensionFilter videoFilter = new FileChooser.ExtensionFilter("video file", "*.mp4", "*.avi", "*.flv", "*.mkv");
            fileChooser.getExtensionFilters().add(videoFilter);
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            // 启用新线程处理业务
            new Thread(() -> {
                if (selectedFile != null) {
                    Platform.runLater(() -> { // 禁用主布局下的其他控件
                        maskLayer.setVisible(true);
                        mainLayout.setDisable(true);
                    });
                    lastInputSelectDirectory = new File(selectedFile.getParent());
                    // 检查文件大小是否超过8MB
                    long fileSizeInBytes = selectedFile.length();
                    long maxSizeInBytes = 8 * 1024 * 1024;
                    if (fileSizeInBytes > maxSizeInBytes) {  // 如果文件过大，显示警告信息
                        Platform.runLater(() -> {
                            maskLayer.setVisible(false);
                            mainLayout.setDisable(false);
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Warn");
                            alert.setHeaderText("Video file is too large！");
                            alert.setContentText("Video file size should less than 32MB!");
                            alert.showAndWait();
                        });
                    } else {
                        inputFilePath = selectedFile.getAbsolutePath();
                        videoPathField.setText(inputFilePath);
                        VideoInfo videoInfo = null;
                        try {
                            videoInfo = FfMpegUtils.readVideoInfo(inputFilePath);
                        } catch (IOException ioe) {
                            Platform.runLater(() -> {
                                LOGGER.warn("Can not read Video Info: {}", ExceptionUtils.toString(ioe));
                                maskLayer.setVisible(false);
                                mainLayout.setDisable(false);
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Warn");
                                alert.setHeaderText("Can not read Video Info");
                                alert.setContentText(ExceptionUtils.toString(ioe));
                                alert.showAndWait();
                            });
                        } catch (RuntimeException re) {
                            maskLayer.setVisible(false);
                            mainLayout.setDisable(false);
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("ERROR");
                                alert.setHeaderText("Error Occur! The window will close");
                                alert.setContentText(re.getMessage());
                                alert.showAndWait();
                                Platform.exit();
                            });
                        }
                        Platform.runLater(() -> {
                            outputPathButton.setDisable(false);
                            outputPathField.setDisable(false);
                            maskLayer.setVisible(false);
                            mainLayout.setDisable(false);
                        });
                        if (videoInfo != null) {
                            frameRateField.setText(Double.toString(videoInfo.getFrameRate()));
                            widthField.setText(Integer.toString(videoInfo.getWidth()));
                            heightField.setText(Integer.toString(videoInfo.getHeight()));
                        }
                        if (isInputOutPutPathFilled()) {
                            startButton.setDisable(false);
                        }
                    }
                }
            }).start();
        });
        // 输出目录按钮
        outputPathButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            if (lastOutputSelectDirectory != null) {
                directoryChooser.setInitialDirectory(lastOutputSelectDirectory);
            }
            File selectedDirectory = directoryChooser.showDialog(primaryStage);
            if (selectedDirectory != null) {
                if (selectedDirectory != null) {
                    lastOutputSelectDirectory = selectedDirectory;
                }
                String outputFolder = selectedDirectory.getAbsolutePath();
                String fileNameWithOutExtension = Paths.get(inputFilePath).getFileName().toString().replaceFirst("[.][^.]+$", "");
                outputPathField.setText(outputFolder + File.separator + fileNameWithOutExtension + ".gif");
                outputFilePath = outputPathField.getText();
                if (isInputOutPutPathFilled()) {
                    startButton.setDisable(false);
                }
            }
        });
        // 输出位置文本框
        outputPathField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // 当焦点从TextField中移出时，newValue将会是false
                outputFilePath = outputPathField.getText();
                if (isInputOutPutPathFilled()) {
                    startButton.setDisable(false);
                }
            }
        });
        // 开始按钮事件
        startButton.setOnAction(e -> {
            // 显示遮罩层和进度指示器
            maskLayer.setVisible(true);
            mainLayout.setDisable(true);  // 禁用主布局下的其他控件
            //
            new Thread(() -> {
                frameRate = frameRateField.getText().isEmpty() ? null : Double.parseDouble(frameRateField.getText());
                width = widthField.getText().isEmpty() ? null : Integer.parseInt(widthField.getText());
                height = heightField.getText().isEmpty() ? null : Integer.parseInt(heightField.getText());
                outputFilePath = outputPathField.getText();
                try {
                    FfMpegUtils.convertToGif(inputFilePath, outputFilePath, width, height, frameRate); // todo 要校验入参
                    // 任务完成后，隐藏遮罩和进度指示器，并恢复界面
                    Platform.runLater(() -> {
                        // 启用主布局下的其他控件
                        maskLayer.setVisible(false);
                        mainLayout.setDisable(false);
                        // 显示成功信息
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Info");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText("Succeed!");
                        successAlert.showAndWait();
                    });
                } catch (IOException ioe) {
                    Platform.runLater(() -> {
                        // 启用主布局下的其他控件
                        maskLayer.setVisible(false);
                        mainLayout.setDisable(false);
                        // 显示失败信息
                        Alert errAlert = new Alert(Alert.AlertType.ERROR);
                        errAlert.setTitle("Warn");
                        errAlert.setHeaderText("Failed");
                        errAlert.setContentText(ExceptionUtils.toString(ioe));
                        errAlert.showAndWait();
                    });
                } catch (RuntimeException re) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("ERROR");
                        alert.setHeaderText("Error Occur");
                        alert.setContentText(ExceptionUtils.toString(re));
                        alert.showAndWait();
                        Platform.exit();
                    });
                }
            }).start();
        });
        // 4、ui行为监听和外观设置
        mainLayout.setStyle("-fx-padding: 20; -fx-background-color: #f3f3f3;");
        configButtonType1(selectVideoButton);
        configTextField(videoPathField);
        configButtonType1(outputPathButton);
        configTextField(outputPathField);
        configTextField(frameRateField);
        configTextField(widthField);
        configTextField(heightField);
        configButtonType2(startButton);
        // 5、Alignments and spacing
        videoSelectionLayout.setAlignment(Pos.CENTER_LEFT);
        frameRateLayout.setAlignment(Pos.CENTER_LEFT);
        dimensionLayout.setAlignment(Pos.CENTER_LEFT);
        outputPathLayout.setAlignment(Pos.CENTER_LEFT);
        mainLayout.setAlignment(Pos.TOP_CENTER);
        // 6、场景和舞台 Scene and Stage
        Scene scene = new Scene(rootLayout, 550, 500);
        primaryStage.setTitle("Gif Converter");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);  // 禁止调整窗口大小
        primaryStage.getIcons().add(new Image(GifConverterApp.class.getClassLoader().getResourceAsStream("circle_icon.png")));
        primaryStage.show();
        mainLayout.requestFocus();
    }

    // 画log和version
    private static HBox drawLogoAndVersion() {
        ImageView imageView = new ImageView(new Image(GifConverterApp.class.getClassLoader().getResourceAsStream("circle_icon.png")));
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        Label versionLabel = new Label("v0.0.1");
        versionLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: black;"); // 设置字体大小和颜色
        StackPane stackPane = new StackPane(imageView, versionLabel);
        stackPane.setAlignment(Pos.BOTTOM_RIGHT); // 将 Label 定位在右下角
        HBox hbox = new HBox(stackPane);
        hbox.setAlignment(Pos.CENTER); // 设置HBox中内容居中
        return hbox;
    }

    // 设置文本框样式
    private static void configTextField(TextField textField) {
        textField.setStyle("-fx-border-color: transparent;-fx-border-radius: 4;-fx-background-color: white; -fx-padding: 5;");
        textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) { // 获得焦点时
                textField.setStyle("-fx-border-color: #3578E5FF; -fx-border-radius: 4;-fx-background-color: white; -fx-padding: 5;");
            } else {  // 失去焦点时
                textField.setStyle("-fx-border-color: transparent;-fx-border-radius: 4;-fx-background-color: white; -fx-padding: 5;");
            }
        });
    }


    // 设置button的ui上的样式和行为监听(样式1）
    private static void configButtonType1(Button button) {
        button.setStyle("-fx-background-color: #4285f4; -fx-text-fill: white; -fx-padding: 5 15; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle() + "-fx-background-color: #3578e5;"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #3578e5;", "-fx-background-color: #4285f4;")));
        button.setOnMousePressed(e -> button.setStyle(button.getStyle() + "-fx-background-color: #2a62c4;"));
        button.setOnMouseReleased(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #2a62c4;", "-fx-background-color: #3578e5;")));
    }

    // 设置button的ui上的样式和行为监听(样式2）
    private static void configButtonType2(Button button) {
        button.setStyle("-fx-background-color: #34a853; -fx-text-fill: white; -fx-padding: 10 20; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle() + "-fx-background-color: #298341;"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #298341;", "-fx-background-color: #34a853;")));
        button.setOnMousePressed(e -> button.setStyle(button.getStyle() + "-fx-background-color: #1a542a;"));
        button.setOnMouseReleased(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #1a542a;", "-fx-background-color: #298341;")));
    }

    // 是否输入输出位置被设置了（不是数据校验）
    private boolean isInputOutPutPathFilled() {
        return !StringUtils.isEmpty(inputFilePath) && !StringUtils.isEmpty(outputFilePath);
    }
}
