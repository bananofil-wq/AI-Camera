package se.kim.aicamera
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
 private var capture: ImageCapture?=null; private var camera: Camera?=null; private var provider: ProcessCameraProvider?=null
 private var lens=CameraSelector.LENS_FACING_BACK; private var flashMode=ImageCapture.FLASH_MODE_OFF; private var timerSeconds=0; private var mode="photo"
 private lateinit var status:TextView; private lateinit var previewView:PreviewView
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startCamera()else status.text="Kamerabehörighet krävs"}
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);status=findViewById(R.id.status);previewView=findViewById(R.id.preview)
  findViewById<TextView>(R.id.shutter).setOnClickListener{v->if(timerSeconds>0){status.text="Tar foto om "+timerSeconds+" s";v.postDelayed({takePhoto()},timerSeconds*1000L)}else takePhoto()}
  findViewById<TextView>(R.id.flash).setOnClickListener{v->flashMode=when(flashMode){ImageCapture.FLASH_MODE_OFF->ImageCapture.FLASH_MODE_AUTO;ImageCapture.FLASH_MODE_AUTO->ImageCapture.FLASH_MODE_ON;else->ImageCapture.FLASH_MODE_OFF};capture?.flashMode=flashMode;(v as TextView).text=when(flashMode){ImageCapture.FLASH_MODE_AUTO->"Blixt auto";ImageCapture.FLASH_MODE_ON->"Blixt på";else->"Blixt av"}}
  findViewById<TextView>(R.id.timer).setOnClickListener{v->timerSeconds=when(timerSeconds){0->3;3->10;else->0};(v as Button).text=if(timerSeconds==0)"Timer av" else "Timer "+timerSeconds+"s"}
  findViewById<TextView>(R.id.gridBtn).setOnClickListener{val g=findViewById<View>(R.id.grid);g.visibility=if(g.visibility==View.VISIBLE)View.GONE else View.VISIBLE}
  findViewById<TextView>(R.id.switchCamera).setOnClickListener{lens=if(lens==CameraSelector.LENS_FACING_BACK)CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK;bindCamera()}
  findViewById<TextView>(R.id.zoom05).setOnClickListener{setZoom(0.5f)};findViewById<TextView>(R.id.zoom1).setOnClickListener{setZoom(1f)};findViewById<TextView>(R.id.zoom2).setOnClickListener{setZoom(2f)}
  findViewById<TextView>(R.id.modePhoto).setOnClickListener{selectMode("photo","Foto")};findViewById<TextView>(R.id.modePortrait).setOnClickListener{selectMode("portrait","Porträtt • AI-bokeh")};findViewById<TextView>(R.id.modeNight).setOnClickListener{selectMode("night","Natt • AI-optimering")}
  findViewById<SeekBar>(R.id.exposure).setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){val st=camera?.cameraInfo?.exposureState?:return;if(st.isExposureCompensationSupported)camera?.cameraControl?.setExposureCompensationIndex((p-6).coerceIn(st.exposureCompensationRange.lower,st.exposureCompensationRange.upper))};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})
  previewView.setOnTouchListener{_,e->if(e.action==MotionEvent.ACTION_UP){val pt=previewView.meteringPointFactory.createPoint(e.x,e.y);camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(pt).setAutoCancelDuration(3,TimeUnit.SECONDS).build())};true}
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera()else permission.launch(Manifest.permission.CAMERA)}
 private fun setZoom(z:Float){val st=camera?.cameraInfo?.zoomState?.value?:return;camera?.cameraControl?.setZoomRatio(z.coerceIn(st.minZoomRatio,st.maxZoomRatio))}
 private fun selectMode(m:String,label:String){mode=m;status.text=label;bindCamera()}
 private fun startCamera(){val f=ProcessCameraProvider.getInstance(this);f.addListener({provider=f.get();bindCamera()},ContextCompat.getMainExecutor(this))}
 private fun bindCamera(){val p=provider?:return;val pr=Preview.Builder().build().also{it.setSurfaceProvider(previewView.surfaceProvider)};capture=ImageCapture.Builder().setCaptureMode(if(mode=="night")ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(flashMode).build();try{p.unbindAll();camera=p.bindToLifecycle(this,CameraSelector.Builder().requireLensFacing(lens).build(),pr,capture)}catch(e:Exception){status.text="Kameraläget stöds inte"}}
 private fun takePhoto(){val c=capture?:return;val name="IMG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(System.currentTimeMillis())+".jpg";val cv=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/Camera")};val out=ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv).build();status.text="Tar foto…";c.takePicture(out,ContextCompat.getMainExecutor(this),object:ImageCapture.OnImageSavedCallback{override fun onImageSaved(r:ImageCapture.OutputFileResults){val u=r.savedUri?:return;status.text="Original sparat • AI bearbetar";val d=Data.Builder().putString("source",u.toString()).putString("name","AI_"+name).putString("mode",mode).build();val q=OneTimeWorkRequestBuilder<AiEditWorker>().setInputData(d).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).build();WorkManager.getInstance(this@MainActivity).enqueue(q);WorkManager.getInstance(this@MainActivity).getWorkInfoByIdLiveData(q.id).observe(this@MainActivity){w->when(w?.state){WorkInfo.State.SUCCEEDED->status.text="AI-bild klar • AI Camera";WorkInfo.State.FAILED->status.text=w.outputData.getString("error")?:"AI-redigeringen misslyckades";else->{}}}};override fun onError(e:ImageCaptureException){status.text="Kamerafel: "+e.message}})}
}