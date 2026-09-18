package com.esn.jarvis
object JarvisLocalPlanner{
 fun plan(goal:String,state:AgentSnapshot):List<AgentStep>{
  val raw=goal.trim();val g=raw.lowercase()
  val chained=raw.split(Regex("\\s+(?:and then|then|after that)\\s+",RegexOption.IGNORE_CASE))
  if(chained.size>1)return chained.flatMap{plan(it,state)}.take(12)
  if(g.startsWith("tap ")||g.startsWith("click "))return listOf(AgentStep.Tap(raw.substringAfter(" ").trim()))
  if(g.startsWith("type ")||g.startsWith("enter "))return listOf(AgentStep.Type(raw.substringAfter(" ").trim()))
  if(g=="go back"||g=="back")return listOf(AgentStep.Back)
  if(g.startsWith("scroll"))return listOf(AgentStep.Scroll)
  val quoted=Regex("[\"']([^\"']+)[\"']").find(raw)?.groupValues?.getOrNull(1)
  if(quoted!=null&&state.text.any{it.contains(quoted,true)})return listOf(AgentStep.Tap(quoted))
  val target=state.text.filter{it.length in 2..60}.maxByOrNull{label->if(g.contains(label.lowercase()))label.length else -1}
  return if(target!=null&&g.contains(target.lowercase()))listOf(AgentStep.Tap(target)) else emptyList()
 }
}