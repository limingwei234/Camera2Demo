package com.mwli.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var childHandler: Handler
    private lateinit var mainHandler: Handler
    private lateinit var mCameraID: String
    private lateinit var mImageReader: ImageReader
    private lateinit var mCameraManager: CameraManager
    private lateinit var mCameraDevice: CameraDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById<SurfaceView>(R.id.surface)

        findViewById<Button>(R.id.takePic).setOnClickListener { takePicture() }

        surfaceHolder = surfaceView.holder

        surfaceHolder.addCallback(object :SurfaceHolder.Callback{

            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                initCamera2()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                releaseCamera2()
            }

        })
    }

    private fun releaseCamera2() {
        mCameraDevice?.close()
    }


    private val stateCallback: CameraDevice.StateCallback = object :CameraDevice.StateCallback() {
        override fun onDisconnected(camera: CameraDevice) {
            mCameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
        }

        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startPreview()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initCamera2() {
        val handlerThread = HandlerThread("Camera2")
        handlerThread.start()

        childHandler = Handler(handlerThread.looper)
        mainHandler = Handler(mainLooper)
        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT //后摄像头
        mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1)
        mImageReader.setOnImageAvailableListener({ reader ->
            //可以在这里处理拍照得到的临时照片 例如，写入本地
            //mCameraDevice.close();
            // 拿到拍照照片数据
            val image: Image = reader.acquireNextImage()
            val buffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes) //由缓冲区存入字节数组
//            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//            if (bitmap != null) {
//                iv_show.setImageBitmap(bitmap)
//            }
        }, mainHandler)
        //获取摄像头管理

        mCameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
                //打开摄像头
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private lateinit var mCameraCaptureSession: CameraCaptureSession
    /**
     * 开始预览
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startPreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            val previewRequestBuilder: CaptureRequest.Builder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(surfaceHolder.surface)
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(
                listOf(
                    surfaceHolder.surface,
                    mImageReader.surface
                ), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == mCameraDevice) return
                        // 当摄像头已经准备好时，开始显示预览
                        mCameraCaptureSession = cameraCaptureSession
                        try {
                            // 自动对焦
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // 打开闪光灯
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )
                            // 显示预览
                            val previewRequest = previewRequestBuilder.build()
                            mCameraCaptureSession.setRepeatingRequest(
                                previewRequest,
                                null,
                                childHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.i("cameraDemoe", "onConfigureFailed")

                    }
                }, childHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * 拍照
     */
    private fun takePicture() {
        if (mCameraDevice == null) return
        // 创建拍照需要的CaptureRequest.Builder
        val captureRequestBuilder: CaptureRequest.Builder
        try {
            captureRequestBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.surface)
            // 自动对焦
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            // 自动曝光
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
            // 获取手机方向
            val rotation: Int = windowManager.defaultDisplay.rotation
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(
                CaptureRequest.JPEG_ORIENTATION,
                ORIENTATIONS.get(rotation)
            )
            //拍照
            val mCaptureRequest = captureRequestBuilder.build()
            mCameraCaptureSession.capture(mCaptureRequest, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Toast.makeText(this@MainActivity, "ok", Toast.LENGTH_SHORT).show()
                }
            }, childHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    companion object{
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

}
