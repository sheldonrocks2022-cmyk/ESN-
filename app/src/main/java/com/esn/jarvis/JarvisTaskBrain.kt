package com.esn.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object JarvisTaskBrain {
 private const val PREFS="jarvis_task_brain"
 private const val MAX_QUEUE=12
 private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)

 fun create(c:Context,goal:String):String {
  val g=goal.trim()
  if(g.isBlank()) return "Tell me the objective."
  val id=System.currentTimeMillis().toString()
  val task=JSONObject().put("id",id).put("goal",g.take(600)).put("state","queued").put("created",System.currentTimeMillis()).put("attempts",0)
  val q=load(c);q.put(task);save(c,trim(q));p(c).edit().putString("active_id",id).apply()
  return "Task queued. Objective: $g."
 }

 fun run(c:Context,goal:String):String {
  val queued=create(c,goal)
  if(!JarvisAccessibilityService.isEnabled(c)) return queued+" Enable Phone Access before execution."
  if(!JarvisAccessibilityService.hasAccess()) return queued+" Phone Access is enabled but not connected."
  val task=active(c)?:return "I could not create the task."
  task.put("state","running").put("started",System.currentTimeMillis()).put("checkpoint",JarvisAgentTools.snapshot().signature.take(1000))
  replace(c,task)
  return try {
   val result=JarvisUnifiedAgent.execute(c,task.optString("goal"))
   task.put("state",classify(result)).put("result",result.take(1000)).put("completed",System.currentTimeMillis())
   replace(c,task)
   result
  } catch(t:Throwable) {
   JarvisDiagnostics.recordFailure(c,"task_brain",t.javaClass.simpleName)
   task.put("state","failed").put("result",t.javaClass.simpleName).put("completed",System.currentTimeMillis())
   replace(c,task)
   "The task stopped safely because JARVIS hit an internal error."
  }
 }

 fun resume(c:Context):String {
  val task=active(c)?:return "There is no task to resume."
  val state=task.optString("state")
  if(state=="completed") return "The active task is already completed."
  val attempts=task.optInt("attempts",0)
  if(attempts>=3) return "I will not retry this task again automatically because it reached the recovery limit."
  task.put("attempts",attempts+1).put("state","recovering").put("last_resume",System.currentTimeMillis());replace(c,task)
  return runExisting(c,task)
 }

 private fun runExisting(c:Context,task:JSONObject):String {
  if(!JarvisAccessibilityService.hasAccess()) return "Phone Access is not connected, so I kept the task checkpoint instead of guessing."
  return try {
   val result=JarvisUnifiedAgent.execute(c,task.optString("goal"))
   task.put("state",classify(result)).put("result",result.take(1000)).put("completed",System.currentTimeMillis()).put("checkpoint",JarvisAgentTools.snapshot().signature.take(1000));replace(c,task);result
  } catch(t:Throwable) {
   JarvisDiagnostics.recordFailure(c,"task_brain_resume",t.javaClass.simpleName);task.put("state","failed").put("result",t.javaClass.simpleName);replace(c,task);"Recovery stopped safely because JARVIS hit an internal error."
  }
 }

 fun stop(c:Context):String {
  val task=active(c)?:return "There is no active task."
  task.put("state","stopped").put("stopped",System.currentTimeMillis());replace(c,task)
  JarvisUnifiedAgent.stop(c)
  return "Task stopped and its checkpoint was preserved."
 }

 fun status(c:Context):String {
  val t=active(c)?:return "Task Brain 10.0 is ready. No active task."
  return "Task Brain 10.0: "+t.optString("state","unknown")+". Objective: "+t.optString("goal")+". Recovery attempts: "+t.optInt("attempts",0)+"."
 }

 fun recoverable(c:Context):Boolean {
  val s=active(c)?.optString("state").orEmpty()
  return s in setOf("queued","running","recovering","stopped","failed")
 }

 fun history(c:Context):String {
  val q=load(c);if(q.length()==0)return "No Task Brain history yet."
  val out=mutableListOf<String>();for(i in (q.length()-5).coerceAtLeast(0) until q.length()){val t=q.optJSONObject(i)?:continue;out+=t.optString("state")+": "+t.optString("goal").take(120)}
  return out.joinToString(" | ")
 }

 private fun classify(r:String):String {
  val x=r.lowercase()
  return if(x.contains("stopped safely")||x.contains("couldn't")||x.contains("could not")||x.contains("safety limit")||x.contains("stalled")||x.contains("can't complete that one yet"))"needs_attention" else "completed"
 }
 private fun active(c:Context):JSONObject? {val id=p(c).getString("active_id","").orEmpty();val q=load(c);for(i in q.length()-1 downTo 0){val t=q.optJSONObject(i)?:continue;if(id.isBlank()||t.optString("id")==id)return t};return null}
 private fun replace(c:Context,task:JSONObject){val q=load(c);val out=JSONArray();var found=false;for(i in 0 until q.length()){val t=q.optJSONObject(i)?:continue;if(t.optString("id")==task.optString("id")){out.put(task);found=true}else out.put(t)};if(!found)out.put(task);save(c,trim(out))}
 private fun load(c:Context)=try{JSONArray(p(c).getString("queue","[]"))}catch(_:Throwable){JSONArray()}
 private fun save(c:Context,q:JSONArray){p(c).edit().putString("queue",q.toString()).apply()}
 private fun trim(q:JSONArray):JSONArray{val out=JSONArray();for(i in (q.length()-MAX_QUEUE).coerceAtLeast(0) until q.length())q.optJSONObject(i)?.let{out.put(it)};return out}
}