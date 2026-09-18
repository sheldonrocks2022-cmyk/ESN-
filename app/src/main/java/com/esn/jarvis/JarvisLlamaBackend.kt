package com.esn.jarvis
import java.io.File
object JarvisLlamaBackend:JarvisModelRuntime.Backend{
 @Volatile private var handle=0L
 @Volatile private var available=false
 init{available=try{System.loadLibrary("jarvis_llama");true}catch(_:Throwable){false}}
 fun install(){if(available)JarvisModelRuntime.installBackend(this)}
 fun isAvailable()=available
 @Synchronized override fun load(model:File):Boolean{if(!available||!model.isFile)return false;if(handle!=0L)nativeFree(handle);handle=nativeLoad(model.absolutePath,4096,Runtime.getRuntime().availableProcessors().coerceIn(2,8));return handle!=0L}
 @Synchronized override fun complete(prompt:String):String=if(handle==0L)"" else nativeComplete(handle,prompt,256)
 private external fun nativeLoad(path:String,contextSize:Int,threads:Int):Long
 private external fun nativeComplete(handle:Long,prompt:String,maxTokens:Int):String
 private external fun nativeFree(handle:Long)
}