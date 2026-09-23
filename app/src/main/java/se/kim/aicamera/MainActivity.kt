package se.kim.aicamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
 private var capture: ImageCapture? = null
 private lateinit var status: TextView
 private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) startCamera() else status.text="Kamerabehörighet krävs" }

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
  status=findViewById(R.id.status); findViewById<Button>(R.id.shutter).setOnClickListener { takePhoto() }
  if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
 }
 private fun startCamera() {
  val pv=findViewById<PreviewView>(R.id.preview)
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   val provider=future.get()
   val preview=Preview.Builder().build().also{it.setSurfaceProvider(pv.surfaceProvider)}
   capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
   provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture)
  },ContextCompat.getMainExecutor(this))
 }
 private fun takePhoto() {
  val c=capture?:return
  val name="IMG_"+SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(System.currentTimeMillis())+".jpg"
  val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,name); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/Camera") }
  val out=ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build()
  status.text="Tar foto…"
  c.takePicture(out,ContextCompat.getMainExecutor(this),object:ImageCapture.OnImageSavedCallback{
   override fun onImageSaved(r:ImageCapture.OutputFileResults){ status.text="Original sparat • AI bearbetar"; val uri=r.savedUri?:return
    val data=Data.Builder().putString("source",uri.toString()).putString("name","AI_"+name).build()
    WorkManager.getInstance(this@MainActivity).enqueue(OneTimeWorkRequestBuilder<AiEditWorker>().setInputData(data).build())
   }
   override fun onError(e:ImageCaptureException){status.text="Kamerafel: "+e.message}
  })
 }
}
