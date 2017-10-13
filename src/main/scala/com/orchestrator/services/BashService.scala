package com.orchestrator.services

import akka.actor.ActorSystem
import akka.event.Logging
import com.orchestrator.actors.Task

import java.io.File
import sys.process._

class BashService(implicit val system: ActorSystem) extends TaskRunnerService {
  lazy val log = Logging(system, classOf[BashService])

  def runTask(task: Task): TaskResult = {
    var outputHolder: String = ""
    ProcessLogger((s: String) => outputHolder = s)
    log.info(s"Running ${task.name}: ${task.command}")

    val command = createCommand(task.command)

    val result = command ! ProcessLogger((s: String) => outputHolder += s)
    if (result == 0) {
      log.info(s"Task succeeded: Output: $outputHolder")
      Success
    } else {
      log.error(s"Task Failed. Output: $outputHolder")
      Failure
    }
  }

  // poorly deal with pipes and files...
  def createCommand(cmd: String): ProcessBuilder = {
    var command: Option[ProcessBuilder] = None
    if (cmd.contains("|")) {
      val split = cmd.split("|").map(_.trim)
      command = split.foldLeft(Option.empty[ProcessBuilder]) { case (a, b) => a.fold(Some(stringToProcess(b))) { aa => Some(aa #| b) } }
    } else if (cmd.contains(">>")) {
      val split = cmd.split(">>").map(_.trim)
      command =
        if (split.length == 2) Some(split(0) #>> new File(split(1)))
        else Some(cmd)
    } else if (cmd.contains(">")) {
      val split = cmd.split(">").map(_.trim)
      command =
        if (split.length == 2) Some(split(0) #> new File(split(1)))
        else Some(cmd)
    }
    command.getOrElse(stringToProcess(cmd))
  }
}
