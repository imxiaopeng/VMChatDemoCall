package com.vmloft.develop.app.demo.call;

import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.hyphenate.chat.EMCallManager;
import com.hyphenate.chat.EMCallStateChangeListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.exceptions.HyphenateException;
import com.hyphenate.media.EMLocalSurfaceView;
import com.hyphenate.media.EMOppositeSurfaceView;
import com.superrtc.sdk.VideoView;
import com.vmloft.develop.library.tools.utils.VMDimenUtil;
import com.vmloft.develop.library.tools.utils.VMLog;
import com.vmloft.develop.library.tools.widget.VMCameraPreview;
import java.io.File;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by lzan13 on 2016/10/18.
 * 视频通话界面处理
 */
public class VideoCallActivity extends CallActivity {

    // 视频通话帮助类
    private EMCallManager.EMVideoCallHelper videoCallHelper;
    // SurfaceView 控件状态，-1 表示通话未接通，0 表示本小远大，1 表示远小本大
    private int surfaceViewState = -1;

    private VMCameraPreview preview = null;
    //private EMLocalSurfaceView localSurface = null;
    private EMOppositeSurfaceView oppositeView = null;

    // 使用 ButterKnife 注解的方式获取控件
    @BindView(R.id.layout_root) View rootView;
    @BindView(R.id.btn_change_call_view) Button changeCallViewBtn;
    @BindView(R.id.layout_call_control) View controlLayout;
    @BindView(R.id.layout_surface_container) RelativeLayout surfaceLayout;

    @BindView(R.id.btn_exit_full_screen) ImageButton exitFullScreenBtn;
    @BindView(R.id.text_call_state) TextView callStateView;
    @BindView(R.id.text_call_time) TextView callTimeView;
    @BindView(R.id.btn_mic_switch) ImageButton micSwitch;
    @BindView(R.id.btn_camera_switch) ImageButton cameraSwitch;
    @BindView(R.id.btn_speaker_switch) ImageButton speakerSwitch;
    @BindView(R.id.btn_record_switch) ImageButton recordSwitch;
    @BindView(R.id.btn_screenshot) ImageButton screenshotSwitch;
    @BindView(R.id.btn_change_camera_switch) ImageButton changeCameraSwitch;
    @BindView(R.id.fab_reject_call) FloatingActionButton rejectCallFab;
    @BindView(R.id.fab_end_call) FloatingActionButton endCallFab;
    @BindView(R.id.fab_answer_call) FloatingActionButton answerCallFab;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        ButterKnife.bind(this);

        initView();
    }

    /**
     * 重载父类方法,实现一些当前通话的操作，
     */
    @Override protected void initView() {
        super.initView();
        if (CallManager.getInstance().isInComingCall()) {
            endCallFab.setVisibility(View.GONE);
            answerCallFab.setVisibility(View.VISIBLE);
            rejectCallFab.setVisibility(View.VISIBLE);
            callStateView.setText(R.string.call_connected_is_incoming);
        } else {
            endCallFab.setVisibility(View.VISIBLE);
            answerCallFab.setVisibility(View.GONE);
            rejectCallFab.setVisibility(View.GONE);
            callStateView.setText(R.string.call_connecting);
        }

        micSwitch.setActivated(!CallManager.getInstance().isOpenMic());
        cameraSwitch.setActivated(!CallManager.getInstance().isOpenCamera());
        speakerSwitch.setActivated(CallManager.getInstance().isOpenSpeaker());
        recordSwitch.setActivated(CallManager.getInstance().isOpenRecord());

        // 初始化视频通话帮助类
        videoCallHelper = EMClient.getInstance().callManager().getVideoCallHelper();

        // 初始化显示通话画面
        initLocalSurfaceView();
        // 判断当前通话时刚开始，还是从后台恢复已经存在的通话
        if (CallManager.getInstance().getCallState() == CallManager.CallState.ACCEPTED) {
            endCallFab.setVisibility(View.VISIBLE);
            answerCallFab.setVisibility(View.GONE);
            rejectCallFab.setVisibility(View.GONE);
            callStateView.setText(R.string.call_accepted);
            refreshCallTime();
            surfaceViewState = 0;
            changeCallView();
        }

        try {
            // 设置默认摄像头为前置
            EMClient.getInstance().callManager().setCameraFacing(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } catch (HyphenateException e) {
            e.printStackTrace();
        }
        CallManager.getInstance().setCallCameraDataProcessor();
    }

    /**
     * 界面控件点击监听器
     */
    @OnClick({
            R.id.btn_change_call_view, R.id.layout_call_control, R.id.btn_exit_full_screen, R.id.btn_change_camera_switch,
            R.id.btn_mic_switch, R.id.btn_camera_switch, R.id.btn_speaker_switch, R.id.btn_record_switch, R.id.btn_screenshot,
            R.id.fab_reject_call, R.id.fab_end_call, R.id.fab_answer_call
    }) void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_change_call_view:
                if (surfaceViewState == 0) {
                    surfaceViewState = 1;
                    changeCallView();
                } else {
                    surfaceViewState = 0;
                    changeCallView();
                }
                break;
            case R.id.layout_call_control:
                onControlLayout();
                break;
            case R.id.btn_exit_full_screen:
                // 最小化通话界面
                exitFullScreen();
                break;
            case R.id.btn_change_camera_switch:
                // 切换摄像头
                changeCamera();
                break;
            case R.id.btn_mic_switch:
                // 麦克风开关
                onMicrophone();
                break;
            case R.id.btn_camera_switch:
                // 摄像头开关
                onCamera();
                break;
            case R.id.btn_speaker_switch:
                // 扬声器开关
                onSpeaker();
                break;
            case R.id.btn_screenshot:
                // 保存通话截图
                onScreenShot();
                break;
            case R.id.btn_record_switch:
                // 录制开关
                onRecordCall();
                break;
            case R.id.fab_end_call:
                // 结束通话
                endCall();
                break;
            case R.id.fab_reject_call:
                // 拒绝接听通话
                rejectCall();
                break;
            case R.id.fab_answer_call:
                // 接听通话
                answerCall();
                break;
        }
    }

    /**
     * 控制界面的显示与隐藏
     */
    private void onControlLayout() {
        if (controlLayout.isShown()) {
            controlLayout.setVisibility(View.GONE);
        } else {
            controlLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 退出全屏通话界面
     */
    private void exitFullScreen() {
        surfaceLayout.removeAllViews();
        if (preview != null) {
            preview.close();
            preview = null;
        }
        oppositeView = null;
        CallManager.getInstance().addFloatWindow();
        // 结束当前界面
        onFinish();
    }

    /**
     * 切换摄像头
     */
    private void changeCamera() {
        // 根据切换摄像头开关是否被激活确定当前是前置还是后置摄像头
        try {
            if (EMClient.getInstance().callManager().getCameraFacing() == 1) {
                EMClient.getInstance().callManager().switchCamera();
                EMClient.getInstance().callManager().setCameraFacing(0);
                // 设置按钮图标
                changeCameraSwitch.setImageResource(R.drawable.ic_camera_rear_white_24dp);
            } else {
                EMClient.getInstance().callManager().switchCamera();
                EMClient.getInstance().callManager().setCameraFacing(1);
                // 设置按钮图标
                changeCameraSwitch.setImageResource(R.drawable.ic_camera_front_white_24dp);
            }
        } catch (HyphenateException e) {
            e.printStackTrace();
        }
    }

    /**
     * 麦克风开关，主要调用环信语音数据传输方法
     */
    private void onMicrophone() {
        try {
            // 根据麦克风开关是否被激活来进行判断麦克风状态，然后进行下一步操作
            if (micSwitch.isActivated()) {
                // 设置按钮状态
                micSwitch.setActivated(false);
                // 暂停语音数据的传输
                EMClient.getInstance().callManager().resumeVoiceTransfer();
                CallManager.getInstance().setOpenMic(true);
            } else {
                // 设置按钮状态
                micSwitch.setActivated(true);
                // 恢复语音数据的传输
                EMClient.getInstance().callManager().pauseVoiceTransfer();
                CallManager.getInstance().setOpenMic(false);
            }
        } catch (HyphenateException e) {
            VMLog.e("exception code: %d, %s", e.getErrorCode(), e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 摄像头开关
     */
    private void onCamera() {
        try {
            // 根据摄像头开关按钮状态判断摄像头状态，然后进行下一步操作
            if (cameraSwitch.isActivated()) {
                // 设置按钮状态
                cameraSwitch.setActivated(false);
                // 暂停视频数据的传输
                EMClient.getInstance().callManager().resumeVideoTransfer();
                CallManager.getInstance().setOpenCamera(true);
            } else {
                // 设置按钮状态
                cameraSwitch.setActivated(true);
                // 恢复视频数据的传输
                EMClient.getInstance().callManager().pauseVideoTransfer();
                CallManager.getInstance().setOpenCamera(false);
            }
        } catch (HyphenateException e) {
            VMLog.e("exception code: %d, %s", e.getErrorCode(), e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 扬声器开关
     */
    private void onSpeaker() {
        // 根据按钮状态决定打开还是关闭扬声器
        if (speakerSwitch.isActivated()) {
            // 设置按钮状态
            speakerSwitch.setActivated(false);
            CallManager.getInstance().closeSpeaker();
            CallManager.getInstance().setOpenSpeaker(false);
        } else {
            // 设置按钮状态
            speakerSwitch.setActivated(true);
            CallManager.getInstance().openSpeaker();
            CallManager.getInstance().setOpenSpeaker(true);
        }
    }

    /**
     * 录制视屏通话内容
     */
    private void onRecordCall() {
        // 根据开关状态决定是否开启录制
        if (recordSwitch.isActivated()) {
            // 设置按钮状态
            recordSwitch.setActivated(false);
            String path = videoCallHelper.stopVideoRecord();
            CallManager.getInstance().setOpenRecord(false);
            File file = new File(path);
            if (file.exists()) {
                Toast.makeText(activity, "录制视频成功 " + path, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "录制失败/(ㄒoㄒ)/~~", Toast.LENGTH_LONG).show();
            }
        } else {
            // 设置按钮状态
            recordSwitch.setActivated(true);
            // 先创建文件夹
            String dirPath = getExternalFilesDir("").getAbsolutePath() + "/videos";
            File dir = new File(dirPath);
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
            videoCallHelper.startVideoRecord(dirPath);
            VMLog.d("开始录制视频");
            Toast.makeText(activity, "开始录制", Toast.LENGTH_LONG).show();
            CallManager.getInstance().setOpenRecord(true);
        }
    }

    /**
     * 保存通话截图
     */
    private void onScreenShot() {
        String dirPath = getExternalFilesDir("").getAbsolutePath() + "/videos/";
        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        String path = dirPath + "video_" + System.currentTimeMillis() + ".jpg";
        boolean result = videoCallHelper.takePicture(path);
        if (result) {
            Toast.makeText(activity, "截图保存成功 " + path, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, "截图保存失败/(ㄒoㄒ)/~~", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 接听通话
     */
    @Override protected void answerCall() {
        super.answerCall();
        endCallFab.setVisibility(View.VISIBLE);
        rejectCallFab.setVisibility(View.GONE);
        answerCallFab.setVisibility(View.GONE);
    }

    private void initLocalSurfaceView() {
        preview = VMCameraPreview.newInstance(activity, 640, 480);
        preview.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onControlLayout();
            }
        });

        RelativeLayout.LayoutParams lParams = new RelativeLayout.LayoutParams(0, 0);
        lParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        lParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
        lParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        preview.setLayoutParams(lParams);
        surfaceLayout.addView(preview);
        // 实现预览界面回调接口
        preview.setCameraDataCallback(new VMCameraPreview.CameraDataCallback() {
            @Override public void onCameraDataCallback(byte[] data, int width, int height, int rotation) {
                VMLog.d("onCameraDataCallback data: %d, w: %d, h: %d, r: %d", data.length, width, height, rotation);
                EMClient.getInstance().callManager().inputExternalVideoData(data, width, height, rotation);
            }
        });
    }

    /**
     * 切换通话界面
     */
    private void changeCallView() {
        if (!changeCallViewBtn.isShown()) {
            changeCallViewBtn.setVisibility(View.VISIBLE);
        }

        int width = VMDimenUtil.dp2px(activity, 96);
        int height = VMDimenUtil.dp2px(activity, 128);
        int rightMargin = VMDimenUtil.dp2px(activity, 16);
        int topMargin = VMDimenUtil.dp2px(activity, 96);

        RelativeLayout.LayoutParams localParams = new RelativeLayout.LayoutParams(width, height);
        RelativeLayout.LayoutParams oppositeParams = new RelativeLayout.LayoutParams(width, height);
        switch (surfaceViewState) {
            case 0:
                // 从 -1 -> 0 或者 从 1 -> 0 状态
                if (oppositeView != null) {
                    surfaceLayout.removeView(oppositeView);
                    oppositeView.release();
                    oppositeView = null;
                }

                oppositeParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                oppositeParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                oppositeView = new EMOppositeSurfaceView(activity);
                oppositeView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        onControlLayout();
                    }
                });
                // 将 view 添加到界面
                surfaceLayout.addView(oppositeView, oppositeParams);

                localParams.width = width;
                localParams.height = height;
                localParams.rightMargin = rightMargin;
                localParams.topMargin = topMargin;
                localParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                preview.setLayoutParams(localParams);
                preview.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        surfaceViewState = 1;
                        changeCallView();
                    }
                });
                // 设置本地预览图像显示在最上层
                preview.setZOrderOnTop(true);
                preview.setZOrderMediaOverlay(true);
                break;
            case 1:
                // 从 0 - 1 状态

                localParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
                localParams.height = RelativeLayout.LayoutParams.MATCH_PARENT;
                preview.setLayoutParams(localParams);
                preview.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        onControlLayout();
                    }
                });

                if (oppositeView != null) {
                    surfaceLayout.removeView(oppositeView);
                    oppositeView.release();
                    oppositeView = null;
                }
                oppositeParams.width = width;
                oppositeParams.height = height;
                oppositeParams.rightMargin = rightMargin;
                oppositeParams.topMargin = topMargin;
                oppositeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                oppositeView = new EMOppositeSurfaceView(activity);
                // 设置点击事件
                oppositeView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        surfaceViewState = 0;
                        changeCallView();
                    }
                });
                // 设置远程图像显示在最上层
                oppositeView.setZOrderOnTop(true);
                oppositeView.setZOrderMediaOverlay(true);

                // 将 view 添加到界面
                surfaceLayout.addView(oppositeView, oppositeParams);
                break;
        }
        oppositeView.setScaleMode(VideoView.EMCallViewScaleMode.EMCallViewScaleModeAspectFill);
        EMClient.getInstance().callManager().setSurfaceView(null, oppositeView);
    }

    @Subscribe(threadMode = ThreadMode.MAIN) public void onEventBus(CallEvent event) {
        if (event.isState()) {
            refreshCallView(event);
        }
        if (event.isTime()) {
            // 不论什么情况都检查下当前时间
            refreshCallTime();
        }
    }

    /**
     * 刷新通话界面
     */
    private void refreshCallView(CallEvent event) {
        EMCallStateChangeListener.CallError callError = event.getCallError();
        EMCallStateChangeListener.CallState callState = event.getCallState();
        switch (callState) {
            case CONNECTING: // 正在呼叫对方，TODO 没见回调过
                VMLog.i("正在呼叫对方" + callError);
                break;
            case CONNECTED: // 正在等待对方接受呼叫申请（对方申请与你进行通话）
                VMLog.i("正在连接" + callError);
                if (CallManager.getInstance().isInComingCall()) {
                    callStateView.setText(R.string.call_connected_is_incoming);
                } else {
                    callStateView.setText(R.string.call_connected);
                }
                break;
            case ACCEPTED: // 通话已接通
                VMLog.i("通话已接通");
                callStateView.setText(R.string.call_accepted);
                // 通话接通，更新界面 UI 显示 TODO 在接通时设置 surfaceview 造成远程图像不显示
                surfaceViewState = 0;
                changeCallView();
                break;
            case DISCONNECTED: // 通话已中断
                VMLog.i("通话已结束" + callError);
                onFinish();
                break;
            // TODO 3.3.0版本 SDK 下边几个暂时都没有回调
            case NETWORK_UNSTABLE:
                if (callError == EMCallStateChangeListener.CallError.ERROR_NO_DATA) {
                    VMLog.i("没有通话数据" + callError);
                } else {
                    VMLog.i("网络不稳定" + callError);
                }
                break;
            case NETWORK_NORMAL:
                VMLog.i("网络正常");
                break;
            case VIDEO_PAUSE:
                VMLog.i("视频传输已暂停");
                break;
            case VIDEO_RESUME:
                VMLog.i("视频传输已恢复");
                break;
            case VOICE_PAUSE:
                VMLog.i("语音传输已暂停");
                break;
            case VOICE_RESUME:
                VMLog.i("语音传输已恢复");
                break;
            default:
                break;
        }
    }

    /**
     * 刷新通话时间显示
     */
    private void refreshCallTime() {
        int t = CallManager.getInstance().getCallTime();
        int h = t / 60 / 60;
        int m = t / 60 % 60;
        int s = t % 60 % 60;
        String time = "";
        if (h > 9) {
            time = "" + h;
        } else {
            time = "0" + h;
        }
        if (m > 9) {
            time += ":" + m;
        } else {
            time += ":0" + m;
        }
        if (s > 9) {
            time += ":" + s;
        } else {
            time += ":0" + s;
        }
        if (!callTimeView.isShown()) {
            callTimeView.setVisibility(View.VISIBLE);
        }
        callTimeView.setText(time);
    }

    /**
     * 屏幕方向改变回调方法
     */
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override protected void onUserLeaveHint() {
        //super.onUserLeaveHint();
        exitFullScreen();
    }

    /**
     * 通话界面拦截 Back 按键，不能返回
     */
    @Override public void onBackPressed() {
        //super.onBackPressed();
        exitFullScreen();
    }
}
