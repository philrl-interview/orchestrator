package com.orchestrator.services

import com.orchestrator.actors.{ Job, Task }
import org.scalatest.{ Matchers, WordSpec }
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration._

class JobRunnerSpec extends WordSpec with MockFactory {
  "JobRunner" should {
    "run all jobs if they're succesful" in {
      val task1 = Task("task1", "true")
      val task2 = Task("task2", "true")
      val job1 = Job("job1", Seq(task1, task2), 5.seconds)
      val taskRunner = mock[TaskRunnerService]
      (taskRunner.runTask _).expects(task1).returning(Success)
      (taskRunner.runTask _).expects(task2).returning(Success)
      val jobRunner = new JobRunnerService(taskRunner)
      jobRunner.runJob(job1)
    }
    
    "not run subsequent jobs after a failure" in {
      val task1 = Task("task1", "true")
      val task2 = Task("task2", "false")
      val job1 = Job("job1", Seq(task1, task2), 5.seconds)
      val taskRunner = mock[TaskRunnerService]
      (taskRunner.runTask _).expects(task1).returning(Failure)
      (taskRunner.runTask _).expects(task2).never()
      val jobRunner = new JobRunnerService(taskRunner)
      jobRunner.runJob(job1)
    }
  }
}
