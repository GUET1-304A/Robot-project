package com.orionstar.robotos.fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.orionstar.robotos.R;
import com.orionstar.robotos.base.BaseFragment;
import com.orionstar.robotos.sdk.api.PersonApi;
import com.orionstar.robotos.sdk.api.TTSApi;
import com.orionstar.robotos.sdk.api.ASRApi;
import com.orionstar.robotos.sdk.api.FaceRegisterApi;
import com.orionstar.robotos.sdk.listener.PersonListener;
import com.orionstar.robotos.sdk.listener.TTSListener;
import com.orionstar.robotos.sdk.listener.ASRListener;
import com.orionstar.robotos.sdk.listener.RegisterListener;
import com.orionstar.robotos.sdk.model.Person;
import com.orionstar.robotos.utils.FaceStorage; // 对应模块2的工具类
import com.orionstar.robotos.utils.LogTools; // 官方项目自带日志工具

/**
 * 迎宾功能核心Fragment，基于官方BaseFragment封装
 * 功能：人脸检测→熟人生人判断→陌生人注册→熟人问好
 */
public class WelcomeFragment extends BaseFragment {
    private static final String TAG = "WelcomeFragment";
    private Context mContext;
    private FaceStorage mFaceStorage; // 人脸-名字存储工具
    private boolean isInteracting = false; // 避免重复触发交互（如同时检测多个人脸）

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_welcome, container, false);
        mContext = getActivity();
        initData();
        initFaceListener(); // 初始化人脸监听（核心入口）
        return rootView;
    }

    /**
     * 初始化数据：创建人脸存储工具
     */
    private void initData() {
        mFaceStorage = new FaceStorage(mContext);
        // 初始化TTS（语音合成）：设置默认语速（0.8倍，避免太快）
        TTSApi.getInstance().setSpeed(0.8f);
        // 初始化ASR（语音识别）：设置中文识别
        ASRApi.getInstance().setLanguage(ASRApi.LANGUAGE_CHINESE);
    }

    /**
     * 初始化人脸监听：机器人检测到人脸时触发
     */
    private void initFaceListener() {
        PersonApi.getInstance().registerPersonListener(new PersonListener() {
            @Override
            public void personChanged() {
                // 避免同时处理多个交互（如用户还在回答名字时，又检测到新人脸）
                if (isInteracting) return;

                // 1. 获取当前视野内的人脸列表（只处理1米内的人脸，避免远处误触发）
                int detectDistance = 1; // 检测距离：1米内
                java.util.List<Person> faceList = PersonApi.getInstance().getAllFaceList(detectDistance);
                if (faceList == null || faceList.isEmpty()) {
                    LogTools.d(TAG, "未检测到1米内的人脸");
                    return;
                }

                // 2. 取距离最近的人脸（避免多人干扰，官方推荐逻辑）
                Person targetPerson = getClosestPerson(faceList);
                if (targetPerson == null) return;

                // 3. 判断熟人生人（核心逻辑）
                String remoteFaceId = targetPerson.getRemoteFaceId();
                if (remoteFaceId == null || remoteFaceId.isEmpty()) {
                    // 陌生人：触发注册流程
                    isInteracting = true;
                    handleStranger(targetPerson);
                } else {
                    // 熟人：触发问好流程
                    isInteracting = true;
                    handleFamiliar(targetPerson, remoteFaceId);
                }
            }
        });
    }

    /**
     * 处理陌生人：问名字→语音识别→人脸注册
     */
    private void handleStranger(Person stranger) {
        // 1. 机器人播报引导语（问名字）
        TTSApi.getInstance().speak("您好，我是迎宾机器人，请问怎么称呼您呀？", new TTSListener() {
            @Override
            public void onSpeakFinish() {
                LogTools.d(TAG, "引导语播报完成，开始监听用户回答");
                // 2. 播报完成后，启动语音识别（听用户说名字）
                ASRApi.getInstance().startRecognize(new ASRListener() {
                    @Override
                    public void onResult(String userName) {
                        // 3. 拿到用户名字（去空格，避免无效输入）
                        userName = userName.trim();
                        if (userName.isEmpty()) {
                            TTSApi.getInstance().speak("不好意思，我没听清您的名字，能再说一遍吗？", new TTSListener() {
                                @Override
                                public void onSpeakFinish() {
                                    isInteracting = false; // 重置交互状态，允许重新触发
                                }
                            });
                            return;
                        }

                        LogTools.d(TAG, "识别到用户名字：" + userName);
                        // 4. 获取陌生人的人脸图片（调用官方API，避免直接getFaceImage()的兼容性问题）
                        String personId = stranger.getPersonId();
                        Bitmap faceBitmap = PersonApi.getInstance().getFaceImage(personId);
                        if (faceBitmap == null) {
                            TTSApi.getInstance().speak("不好意思，没看清您的脸，请靠近一点重试~", new TTSListener() {
                                @Override
                                public void onSpeakFinish() {
                                    isInteracting = false;
                                }
                            });
                            return;
                        }

                        // 5. 调用官方人脸注册API，绑定“人脸-名字”
                        FaceRegisterApi.getInstance().registerFace(faceBitmap, userName, new RegisterListener() {
                            @Override
                            public void onSuccess(String newRemoteFaceId) {
                                // 注册成功：存储人脸ID和名字，播报确认
                                mFaceStorage.saveFaceName(newRemoteFaceId, userName);
                                TTSApi.getInstance().speak("好的，" + userName + "，我记住您啦！", new TTSListener() {
                                    @Override
                                    public void onSpeakFinish() {
                                        isInteracting = false; // 重置交互状态
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int errorCode, String errorMsg) {
                                // 注册失败：根据错误码提示（参考官方错误码文档）
                                String tip = "人脸注册失败，请重试~";
                                if (errorCode == 101303) {
                                    tip = "摄像头未授权，无法注册人脸~";
                                } else if (errorCode == 101305) {
                                    tip = "人脸模糊，请调整光线后重试~";
                                }
                                TTSApi.getInstance().speak(tip, new TTSListener() {
                                    @Override
                                    public void onSpeakFinish() {
                                        isInteracting = false;
                                    }
                                });
                                LogTools.e(TAG, "人脸注册失败：code=" + errorCode + ", msg=" + errorMsg);
                            }
                        });
                    }

                    @Override
                    public void onError(int errorCode, String errorMsg) {
                        // ASR识别错误（如无声音输入）
                        TTSApi.getInstance().speak("不好意思，我没听到声音，能再说一遍吗？", new TTSListener() {
                            @Override
                            public void onSpeakFinish() {
                                isInteracting = false;
                            }
                        });
                        LogTools.e(TAG, "ASR识别错误：code=" + errorCode + ", msg=" + errorMsg);
                    }
                });
            }

            @Override
            public void onSpeakError(int errorCode, String errorMsg) {
                // TTS播报错误
                LogTools.e(TAG, "TTS播报错误：code=" + errorCode + ", msg=" + errorMsg);
                isInteracting = false;
            }
        });
    }

    /**
     * 处理熟人：根据人脸ID获取名字→个性化问好
     */
    private void handleFamiliar(Person familiar, String remoteFaceId) {
        // 1. 从存储中获取熟人名字
        String userName = mFaceStorage.getFaceName(remoteFaceId);
        if (userName.isEmpty()) {
            // 异常情况：有remoteFaceId但无名字，降级为陌生人处理
            handleStranger(familiar);
            return;
        }

        // 2. 个性化问好（可扩展：结合上次交互时间，此处简化为基础问候）
        String greetMsg = "您好，" + userName + "！又见到您啦，今天心情怎么样呀？";
        TTSApi.getInstance().speak(greetMsg, new TTSListener() {
            @Override
            public void onSpeakFinish() {
                isInteracting = false; // 重置交互状态
            }

            @Override
            public void onSpeakError(int errorCode, String errorMsg) {
                LogTools.e(TAG, "熟人问候播报错误：code=" + errorCode + ", msg=" + errorMsg);
                isInteracting = false;
            }
        });
    }

    /**
     * 辅助方法：从人脸列表中取距离最近的人脸（根据人脸宽度判断，宽度越大距离越近）
     */
    private Person getClosestPerson(java.util.List<Person> faceList) {
        Person closestPerson = null;
        int maxFaceWidth = 0;
        for (Person person : faceList) {
            // 获取人脸矩形宽度（官方Person类的getRect()方法返回人脸位置）
            int faceWidth = person.getRect().width();
            if (faceWidth > maxFaceWidth) {
                maxFaceWidth = faceWidth;
                closestPerson = person;
            }
        }
        return closestPerson;
    }

    /**
     * Fragment销毁时：释放资源，避免内存泄漏
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 注销人脸监听
        PersonApi.getInstance().unregisterPersonListener();
        // 停止TTS和ASR
        TTSApi.getInstance().stopSpeak();
        ASRApi.getInstance().stopRecognize();
    }
}
