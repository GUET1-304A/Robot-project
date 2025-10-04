package com.orionstar.robotos.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 人脸存储工具类：存储“remoteFaceId（人脸唯一ID）→ userName（名字）”的映射
 * 基于SharedPreferences，适配官方项目的工具类风格
 */
public class FaceStorage {
    // 存储文件名（与官方工具类命名规范一致）
    private static final String SP_NAME = "orionstar_welcome_face_data";
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    public FaceStorage(Context context) {
        // 初始化SharedPreferences（私有模式，仅当前应用可访问）
        mSharedPreferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
    }

    /**
     * 存储：人脸ID→名字
     * @param remoteFaceId 官方人脸注册返回的唯一ID
     * @param userName 用户名字
     */
    public void saveFaceName(String remoteFaceId, String userName) {
        if (remoteFaceId == null || remoteFaceId.isEmpty() || userName == null) {
            LogTools.e("FaceStorage", "存储失败：人脸ID或名字为空");
            return;
        }
        mEditor.putString(remoteFaceId, userName).apply(); // apply()：异步存储，避免阻塞主线程
        LogTools.d("FaceStorage", "存储成功：faceId=" + remoteFaceId + ", name=" + userName);
    }

    /**
     * 获取：根据人脸ID取名字
     * @param remoteFaceId 官方人脸注册返回的唯一ID
     * @return 用户名字（为空表示未找到）
     */
    public String getFaceName(String remoteFaceId) {
        if (remoteFaceId == null || remoteFaceId.isEmpty()) {
            LogTools.e("FaceStorage", "获取失败：人脸ID为空");
            return "";
        }
        return mSharedPreferences.getString(remoteFaceId, "");
    }

    /**
     * （可选）删除：根据人脸ID删除存储（用于后期维护）
     * @param remoteFaceId 官方人脸注册返回的唯一ID
     */
    public void deleteFaceName(String remoteFaceId) {
        if (remoteFaceId == null || remoteFaceId.isEmpty()) {
            LogTools.e("FaceStorage", "删除失败：人脸ID为空");
            return;
        }
        mEditor.remove(remoteFaceId).apply();
        LogTools.d("FaceStorage", "删除成功：faceId=" + remoteFaceId);
    }
}
