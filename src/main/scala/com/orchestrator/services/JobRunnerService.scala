package com.orchestrator.services

import com.orchestrator.actors.Job

class JobRunnerService(taskRunner: TaskRunnerService) {
  def runJob(job: Job): Unit = {
    import scala.util.control.Breaks._
    var result: TaskResult = null
    breakable {
      for (task <- job.tasks) {
        result = taskRunner.runTask(task)
        if (result == Failure) break; // Exits the breakable block
      }
    }

  }

}
