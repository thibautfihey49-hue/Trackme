package com.thibautfihey.trackme
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class CamService {
    companion object {
        const val TAG = "TrackCam"
        const val CAMERA_BACK = "back"
        const val CAMERA_FRONT = "front"
        const val VIDEO_DURATION_MS = 15000 // 15 secondes
        
        // ===== PHOTO =====
        fun takePhotoCompressedSync(
            context: Context,
            cameraChoice: String = CAMERA_BACK,
            quality: Int = 60
        ): ByteArray {
            muteSystemSounds(context)
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw Exception("Permission caméra manquante")
            }
            
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val targetFacing = when(cameraChoice) {
                CAMERA_FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                else -> CameraCharacteristics.LENS_FACING_BACK
            }
            
            val camId = camMgr.cameraIdList.firstOrNull { 
                val chars = camMgr.getCameraCharacteristics(it)
                chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
            } ?: camMgr.cameraIdList.firstOrNull() ?: throw Exception("Caméra non disponible")
            
            Log.d(TAG, "📸 Photo — ${if(targetFacing == CameraCharacteristics.LENS_FACING_FRONT) "AVANT" else "ARRIÈRE"}")
            
            var resultBytes: ByteArray? = null
            val lock = Object()
            
            camMgr.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    try {
                        val reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
                        reader.setOnImageAvailableListener({ reader ->
                            val image = reader.acquireNextImage()
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val output = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, output)
                            resultBytes = output.toByteArray()
                            bmp.recycle()
                            
                            cam.close()
                            reader.close()
                            synchronized(lock) { lock.notify() }
                        }, context.mainExecutor)
                        
                        val surface = reader.surface
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            cam.createCaptureSession(
                                listOf(SurfaceConfiguration(surface)),
                                SessionConfiguration.SESSION_REGULAR,
                                listOf(surface),
                                context.mainExecutor,
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        session.capture(req.build(), null, null)
                                    }
                                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                                        synchronized(lock) { lock.notify() }
                                    }
                                }
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    session.capture(req.build(), null, null)
                                }
                                override fun onConfigureFailed(p0: CameraCaptureSession) {
                                    synchronized(lock) { lock.notify() }
                                }
                            }, null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur photo", e)
                        synchronized(lock) { lock.notify() }
                    }
                }
                override fun onDisconnected(p0: CameraDevice) { synchronized(lock) { lock.notify() } }
                override fun onError(p0: CameraDevice, p1: Int) { synchronized(lock) { lock.notify() } }
            }, null)
            
            synchronized(lock) { lock.wait(20000) }
            return resultBytes ?: throw Exception("Échec capture photo")
        }
        
        // ===== VIDÉO =====
        fun recordVideoSync(
            context: Context,
            cameraChoice: String = CAMERA_BACK,
            durationMs: Long = VIDEO_DURATION_MS
        ): File {
            muteSystemSounds(context)
            
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw Exception("Permissions manquantes")
            }
            
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val targetFacing = when(cameraChoice) {
                CAMERA_FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                else -> CameraCharacteristics.LENS_FACING_BACK
            }
            
            val camId = camMgr.cameraIdList.firstOrNull { 
                val chars = camMgr.getCameraCharacteristics(it)
                chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
            } ?: camMgr.cameraIdList.firstOrNull() ?: throw Exception("Caméra non disponible")
            
            val videoFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "🎥 Vidéo — ${if(targetFacing == CameraCharacteristics.LENS_FACING_FRONT) "AVANT" else "ARRIÈRE"}")
            
            val lock = Object()
            var recordingFailed = false
            
            // Configuration MediaRecorder
            val recorder = MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(640, 480)
                setVideoFrameRate(15)
                setVideoEncodingBitRate(800000)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                prepare()
            }
            
            val thread = HandlerThread("CameraVideo")
            thread.start()
            val handler = Handler(thread.looper)
            
            camMgr.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    try {
                        val surface = recorder.surface
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(surface)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                        
                        cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.setRepeatingRequest(req.build(), null, handler)
                                    recorder.start()
                                    Log.d(TAG, "🎥 Enregistrement démarré")
                                    
                                    // Arrêter après la durée
                                    Handler(context.mainLooper).postDelayed({
                                        try {
                                            recorder.stop()
                                            recorder.release()
                                            cam.close()
                                            Log.d(TAG, "🎥 Enregistrement terminé : ${videoFile.length()} octets")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Erreur arrêt vidéo", e)
                                            recordingFailed = true
                                        }
                                        synchronized(lock) { lock.notify() }
                                    }, durationMs)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erreur démarrage session", e)
                                    recordingFailed = true
                                    synchronized(lock) { lock.notify() }
                                }
                            }
                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                recordingFailed = true
                                synchronized(lock) { lock.notify() }
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur ouverture caméra", e)
                        recordingFailed = true
                        synchronized(lock) { lock.notify() }
                    }
                }
                override fun onDisconnected(p0: CameraDevice) { synchronized(lock) { lock.notify() } }
                override fun onError(p0: CameraDevice, p1: Int) { recordingFailed = true; synchronized(lock) { lock.notify() } }
            }, handler)
            
            synchronized(lock) { lock.wait(durationMs + 10000) }
            thread.quitSafely()
            
            if (recordingFailed || !videoFile.exists() || videoFile.length() < 1000) {
                throw Exception("Échec enregistrement vidéo")
            }
            
            return videoFile
        }
        
        // 🔇 DÉSACTIVER SON ET VIBRATION
        private fun muteSystemSounds(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vibrator.defaultVibrator.cancel()
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    vibrator.cancel()
                }
            } catch (e: Exception) {}
        }
    }
}
