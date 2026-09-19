package com.esn.jarvis
import android.content.Context
import java.io.File
object JarvisCodingWorkspace{
 private fun root(c:Context)=File(c.filesDir,"coding_workspace").apply{mkdirs()}
 private fun safe(c:Context,path:String):File?{val r=root(c);val f=File(r,path).canonicalFile;return if(f.path.startsWith(r.canonicalPath+File.separator))f else null}
 fun write(c:Context,path:String,body:String):String{val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";return try{f.parentFile?.mkdirs();f.writeText(body); "Saved $path in the local coding workspace."}catch(_:Throwable){"I could not write that code file."}}
 fun read(c:Context,path:String):String{val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";return if(!f.isFile)"That code file does not exist." else f.readText().take(6000)}
 fun delete(c:Context,path:String):String{val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";return if(f.isFile&&f.delete())"Deleted $path from the coding workspace." else "That code file does not exist."}
 fun status(c:Context):String{val r=root(c);val files=r.walkTopDown().filter{it.isFile}.take(50).toList();return if(files.isEmpty())"Coding workspace is ready and empty." else "Coding workspace contains "+files.size+" file(s): "+files.take(12).joinToString(", "){it.relativeTo(r).path}}
}