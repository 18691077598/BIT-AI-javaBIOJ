package org.example;

/**
 * 导入进度回调接口。
 */
@FunctionalInterface
public interface ImportProgressCallback {
    /**
     * 回调方法，用于报告导入进度。
     *
     * @param totalRecords     总记录数。
     * @param processedRecords 已处理记录数。
     */
    void onProgress(int totalRecords, int processedRecords);
}
