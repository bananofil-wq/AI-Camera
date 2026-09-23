package se.kim.aicamera
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
class AiEditWorker(ctx:Context,p:WorkerParameters):CoroutineWorker(ctx,p){
 override suspend fun doWork():Result{
  val source=inputData.getString("source")?:return Result.failure();val name=inputData.getString("name")?:"AI_photo.jpg";val mode=inputData.getString("mode")?:"photo"
  return try{val bytes=applicationContext.contentResolver.openInputStream(Uri.parse(source)).use{it?.readBytes()?:return Result.failure(error("Kan inte läsa originalbilden"))};val boundary="AICameraBoundary"+System.currentTimeMillis();val c=(URL("$SUPABASE_URL/functions/v1/edit-photo").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=30000;readTimeout=240000;setRequestProperty("apikey",SUPABASE_KEY);setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")}
   c.outputStream.use{o->fun s(v:String)=o.write(v.toByteArray());s("--$boundary\r\nContent-Disposition: form-data; name=\"mode\"\r\n\r\n$mode\r\n");s("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n");o.write(bytes);s("\r\n--$boundary--\r\n")}
   val code=c.responseCode;val response=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
   if(code !in 200..299){val msg=try{JSONObject(response).optString("details").ifBlank{JSONObject(response).optString("error")}}catch(_:Exception){response};return if(code>=500&&runAttemptCount<2)Result.retry() else Result.failure(error("AI-fel $code: "+msg.take(120)))}
   val b64=JSONObject(response).optString("image_base64");if(b64.isBlank())return Result.failure(error("AI returnerade ingen bild"));val edited=Base64.getDecoder().decode(b64)
   val v=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/AI Camera");put(MediaStore.Images.Media.IS_PENDING,1)}
   val dest=applicationContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v)?:return Result.failure(error("Kan inte skapa AI Camera-albumet"))
   try{applicationContext.contentResolver.openOutputStream(dest).use{it?.write(edited)?:error("destination")};v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);applicationContext.contentResolver.update(dest,v,null,null)}catch(e:Exception){applicationContext.contentResolver.delete(dest,null,null);throw e};Result.success()
  }catch(e:Exception){if(runAttemptCount<2)Result.retry() else Result.failure(error("AI-redigering misslyckades: "+(e.message?:"okänt fel")))}
 }
 private fun error(m:String)=Data.Builder().putString("error",m).build()
 companion object{private const val SUPABASE_URL="https://zqurybtddbrbelalohnl.supabase.co";private const val SUPABASE_KEY="sb_publishable_qMmcBd_0PdHxI0guShM2rg_7UvtuvAf"}
}