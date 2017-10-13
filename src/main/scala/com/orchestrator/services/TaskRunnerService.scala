package com.orchestrator.services

import com.orchestrator.actors.Task

sealed trait TaskResult
case object Success extends TaskResult
case object Failure extends TaskResult

trait TaskRunnerService {
  def runTask(task: Task): TaskResult
}
