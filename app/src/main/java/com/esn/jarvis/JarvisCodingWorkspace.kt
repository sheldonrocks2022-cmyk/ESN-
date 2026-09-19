package com.esn.jarvis
import android.content.Context
import java.io.File
object JarvisCodingWorkspace{
 private fun root(c:Context)=File(c.filesDir,"coding_workspace").apply{mkdirs()}
 private fun safe(c:Context,path:String):File?{val r=root(c).canonicalFile;val f=File(r,path).canonicalFile;return if(f.path==r.path||f.path.startsWith(r.path+File.separator))f else null}
 fun write(c:Context,path:String,content:String):String{if(path.isBlank())return "Give me a workspace file path.";val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";return try{f.parentFile?.mkdirs();f.writeText(content); "Saved $path in the local coding workspace."}catch(_:Throwable){"I could not write that workspace file."}}
 fun read(c:Context,path:String):String{val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";return if(!f.isFile)"I could not find $path." else f.readText().take(6000)}
 fun list(c:Context):String{val r=root(c);val files=r.walkTopDown().filter{it.isFile}.map{it.relativeTo(r).path}.take(50).toList();return if(files.isEmpty())"The coding workspace is empty." else "Workspace files: "+files.joinToString(", ")}
 fun replace(c:Context,path:String,old:String,new:String):String{val f=safe(c,path)?:return "That path is outside the JARVIS coding workspace.";if(!f.isFile)return "I could not find $path.";val src=f.readText();if(!src.contains(old))return "I could not find the requested code in $path.";f.writeText(src.replaceFirst(old,new));return "Updated $path."}
 fun status(c:Context):String{val r=root(c);val count=r.walkTopDown().count{it.isFile};return "Local coding workspace ready with $count files. File creation and editing are available; compilation requires an installed build toolchain."}
}