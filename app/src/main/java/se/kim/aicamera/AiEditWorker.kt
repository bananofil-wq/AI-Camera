package se.kim.aicamera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AiEditWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx,params) {
 override suspend fun doWork(): Result {
  val source=inputData.getString("source")?:return Result.failure()
  val name=inputData.getString("name")?:"AI_photo.jpg"
  // v0.1 pipeline hook: keep the original untouched and create the separate AI output.
  // The remote AI image-edit provider is intentionally isolated here so no API secret is shipped in the APK.
  val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,name); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/AI Camera") }
  val dest=applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:return Result.retry()
  applicationContext.contentResolver.openInputStream(Uri.parse(source)).use { input ->
   applicationContext.contentResolver.openOutputStream(dest).use { output ->
    if(input==null||output==null) return Result.retry()
    input.copyTo(output)
   }
  }
  return Result.success()
 }
 companion object {
  const val EDIT_PROMPT = """Professional full-frame camera look with realistic fast-lens depth of field and natural lens character. Preserve face, body, expression, clothes, identity, objects and composition exactly. Clean only distracting background elements while keeping the same setting and clean subject edges. Correct white balance, recover highlights, open shadows, preserve true skin texture and natural skin tones. Apply a subtle coherent golden-hour treatment only where physically plausible: warm directional light, soft longer shadows and restrained sky/surface warmth. Finish with natural sharpening and noise reduction suitable for high-quality print. Never reshape, beautify or alter identity."""
 }
}
