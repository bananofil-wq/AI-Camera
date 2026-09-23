package se.kim.aicamera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class AiEditWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx, params) {
 override suspend fun doWork(): Result {
  val source = inputData.getString("source") ?: return Result.failure()
  val name = inputData.getString("name") ?: "AI_photo.jpg"
  return try {
   val bytes = applicationContext.contentResolver.openInputStream(Uri.parse(source)).use { input ->
    if (input == null) return Result.retry()
    input.readBytes()
   }
   val boundary = "AICameraBoundary"
   val connection = (URL("$SUPABASE_URL/functions/v1/edit-photo").openConnection() as HttpURLConnection).apply {
    requestMethod = "POST"; doOutput = true
    connectTimeout = 30_000; readTimeout = 180_000
    setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
    setRequestProperty("apikey", SUPABASE_KEY)
    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
   }
   connection.outputStream.use { out ->
    out.write("--$boundary\r\n".toByteArray())
    out.write("Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\n".toByteArray())
    out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
    out.write(bytes)
    out.write("\r\n--$boundary--\r\n".toByteArray())
   }
   val code = connection.responseCode
   val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
    .bufferedReader().use { it.readText() }
   if (code !in 200..299) return if (code >= 500 || code == 429) Result.retry() else Result.failure()

   val b64 = JSONObject(response).optString("image_base64")
   if (b64.isBlank()) return Result.retry()
   val edited = Base64.getDecoder().decode(b64)

   val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, name)
    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/AI Camera")
    put(MediaStore.Images.Media.IS_PENDING, 1)
   }
   val dest = applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return Result.retry()
   try {
    applicationContext.contentResolver.openOutputStream(dest).use { output ->
     if (output == null) throw IllegalStateException("Could not open destination")
     output.write(edited)
    }
    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
    applicationContext.contentResolver.update(dest, values, null, null)
   } catch (e: Exception) {
    applicationContext.contentResolver.delete(dest, null, null)
    throw e
   }
   Result.success()
  } catch (e: Exception) {
   if (runAttemptCount < 3) Result.retry() else Result.failure()
  }
 }
 companion object {
  private const val SUPABASE_URL = "https://zqurybtddbrbelalohnl.supabase.co"
  private const val SUPABASE_KEY = "sb_publishable_qMmcBd_0PdHxI0guShM2rg_7UvtuvAf"
 }
}
