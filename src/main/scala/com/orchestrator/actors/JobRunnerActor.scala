package com.orchestrator.actors

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.model.DateTime
import akka.pattern.ask
import akka.util.Timeout
import com.orchestrator.actors.JobRegistryActor.{ GetJobs, UpdateLastRunAt }
import com.orchestrator.services.{ BashService, JobRunnerService, TaskRunnerService }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

object JobRunnerActor {
  final case class RunJob(job: Job)
  final case class RunTask(task: Task)
  final case object ScheduledRun

  def props: Props = Props[JobRunnerActor]

}

class JobRunnerActor extends Actor with ActorLogging {
  import JobRunnerActor._
  implicit val system = context.system
  val taskRunnerService: TaskRunnerService = new BashService()
  val jobRunnerService: JobRunnerService = new JobRunnerService(taskRunnerService)

  val jobRegistry = context.actorSelection(context.parent.path / "job-registry")

  implicit val executionContext: ExecutionContext = context.system.dispatcher
  implicit lazy val timeout = Timeout(5.seconds)

  override def receive: Receive = {
    case RunJob(job) =>
      Future {
        log.info(s"Running ${job.name}")
        jobRunnerService.runJob(job)
      }
      jobRegistry ! UpdateLastRunAt(job, DateTime.now)

    case RunTask(task) =>
      log.info(s"Running job: ${task.name} (outside of Future)")
      Future {
        log.info(s"Running job: ${task.name}")
        taskRunnerService.runTask(task)
      }

    case ScheduledRun =>
      log.info("Starting scheduled run")
      (jobRegistry ? GetJobs).mapTo[Jobs].foreach { jobs =>
        val now = DateTime.now
        val jobsToRun = jobs.jobs.filter(j => j.lastRunAt.forall(d => (now.clicks - d.clicks) >= j.frequency.toMillis))
        log.info(s"${jobsToRun.length} jobs to run")
        jobsToRun.foreach(j => self ! RunJob(j))
      }
  }

}
