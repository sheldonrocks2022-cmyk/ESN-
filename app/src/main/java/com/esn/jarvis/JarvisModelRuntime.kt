package com.esn.jarvis
import android.content.Context
import java.io.File
/**
 * Local-model runtime contract. Model files live in app-private storage and
 * are never sent to a server. Native backends can implement Backend.
 */
object JarvisModelRuntime{
 interface Backend{fun load(model:File):Boolean;fun complete(prompt:String):String}
 @Volatile private var backend:Backend?=null
 @Volatile private var loadedPath:String?=null
 fun installBackend(value:Backend){backend=value;loadedPath=null}
 fun modelDir(context:Context)=File(context.filesDir,"models").apply{mkdirs()}
 fun modelFile(context:Context)=File(modelDir(context),"jarvis.gguf")
 fun isModelPresent(context:Context)=modelFile(context).let{it.isFile&&it.length()>1_000_000}
 fun isReady(context:Context):Boolean{
  val b=backend?:return false;val f=modelFile(context);if(!isModelPresent(context))return false
  if(loadedPath==f.absolutePath)return true
  return try{if(b.load(f)){loadedPath=f.absolutePath;true}else false}catch(_:Throwable){false}
 }
 fun complete(context:Context,prompt:String):String?=if(isReady(context))try{backend?.complete(prompt)}catch(_:Throwable){null}else null
 fun status(context:Context)=when{backend==null->"NATIVE BACKEND NOT INSTALLED";!isModelPresent(context)->"MODEL FILE NOT INSTALLED";isReady(context)->"LOCAL MODEL READY";else->"MODEL LOAD FAILED"}
}
