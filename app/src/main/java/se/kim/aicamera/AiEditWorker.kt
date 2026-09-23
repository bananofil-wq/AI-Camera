package se.kim.aicamera
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class AiEditWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
 override suspend fun doWork(): Result {
  val source = inputData.getString("source") ?: return Result.failure()
  val name = inputData.getString("name") ?: "AI_photo.jpg"
  val mode = inputData.getString("mode") ?: "photo"
  return try {
   val bytes = applicationContext.contentResolver.openInputStream(Uri.parse(source)).use { input ->
    input?.readBytes() ?: return Result.failure(failureData("Kan inte läsa originalbilden"))
   }
   val boundary = "AICameraBoundary" + System.currentTimeMillis()
   val connection = (URL("$SUPABASE_URL/functions/v1/edit-photo").openConnection() as HttpURLConnection).apply {
    requestMethod = "POST"
    doOutput = true
    connectTimeout = 30000
    readTimeout = 240000
    setRequestProperty("apikey", SUPABASE_KEY)
    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
   }
   connection.outputStream.use { out ->
    fun writeText(value: String) { out.write(value.toByteArray()) }
    writeText("--$boundary\r\n")
    writeText("Content-Disposition: form-data; name=\"mode\"\r\n\r\n")
    writeText("$mode\r\n")
    writeText("--$boundary\r\n")
    writeText("Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\n")
    writeText("Content-Type: image/jpeg\r\n\r\n")
    out.write(bytes)
    writeText("\r\n--$boundary--\r\n")
   }
   val code = connection.responseCode
   val stream = if (code in 200..299) connection.inputStream else connection.errorStream
   val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
   if (code !in 200..299) {
    val msg = try {
     val json = JSONObject(response)
     json.optString("details").ifBlank { json.optString("error") }
    } catch (_: Exception) { response }
    return if (code >= 500 && runAttemptCount < 2) Result.retry()
    else Result.failure(failureData("AI-fel $code: " + msg.take(120)))
   }
   val b64 = JSONObject(response).optString("image_base64")
   if (b64.isBlank()) return Result.failure(failureData("AI returnerade ingen bild"))
   val edited = Base64.getDecoder().decode(b64)
   val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, name)
    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/AI Camera")
    put(MediaStore.Images.Media.IS_PENDING, 1)
   }
   val dest = applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    ?: return Result.failure(failureData("Kan inte skapa AI Camera-albumet"))
   try {
    applicationContext.contentResolver.openOutputStream(dest).use { output ->
     if (output == null) throw IllegalStateException("Kan inte öppna målfilen")
     output.write(edited)
    }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    applicationContext.contentResolver.update(dest, values, null, null)
   } catch (e: Exception) {
    applicationContext.contentResolver.delete(dest, null, null)
    throw e
   }
   Result.success()
  } catch (e: Exception) {
   if (runAttemptCount < 2) Result.retry()
   else Result.failure(failureData("AI-redigering misslyckades: " + (e.message ?: "okänt fel")))
  }
 }
 private fun failureData(message: String): Data = Data.Builder().putString("error", message).build()
 companion object {
  private const val SUPABASE_URL = "https://zqurybtddbrbelalohnl.supabase.co"
  private const val SUPABASE_KEY = "sb_publishable_qMmcBd_0PdHxI0guShM2rg_7UvtuvAf"
 }
}