package org.jugo.utils;

public class ExceptionUtils {
    /**
     * 输出exception堆栈信息
     *
     * @param exception 异常
     * @return exception堆栈信息
     */
    public static String toString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTrace = exception.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            sb.append(stackTraceElement.toString()).append(System.lineSeparator());
        }
        return sb.substring(0, sb.length() - 1);
    }
}
