package com.orchestrator.actors

import akka.actor.{ Actor, ActorLogging, Props }
import akka.http.scaladsl.model.DateTime
import com.orchestrator.actors.JobRunnerActor.RunJob
import com.orchestrator._

import scala.concurrent.duration._

case class Task(name: String, command: String)

case class Job(name: String, tasks: Seq[Task], frequency: Duration, lastRunAt: Option[DateTime] = None)

case class Tasks(tasks: List[Task])

case class Jobs(jobs: List[Job])

object JobRegistryActor {
  final case class ActionPerformed(description: String)
  final case class ActionFailed(description: String)
  final case object GetTasks
  final case object GetJobs

  final case class GetJob(job: String)
  final case class GetTask(task: String)

  final case class CreateJob(job: JobRequest)
  final case class CreateTask(task: TaskRequest)

  final case class DeleteJob(job: String)

  final case class StartJob(job: String)
  final case class StartTask(task: String)
  final case class UpdateLastRunAt(job: Job, time: DateTime)

  def props: Props = Props[JobRegistryActor]

}

class JobRegistryActor extends Actor with ActorLogging {
  import JobRegistryActor._

  val jobRunner = context.actorSelection(context.parent.path / "job-runner")

  var tasks: Set[Task] = Set.empty[Task]
  var jobs: Set[Job] = Set.empty[Job]

  def requestToJob(job: JobRequest): Job = {
    val jobTasks = job.tasks.map { t => tasks.find(t == _.name) }
    if (jobTasks.exists(_.isEmpty)) throw new RuntimeException("unknown task")
    Job(job.name, jobTasks.flatten, job.frequency.toDuration)
  }

  def requestToTask(task: TaskRequest): Task =
    Task(task.name, task.command)

  def receive: Receive = {
    case GetTasks =>
      sender() ! Tasks(tasks.toList)

    case GetJobs =>
      sender() ! Jobs(jobs.toList)

    case GetJob(job) =>
      sender() ! jobs.find(_.name == job)

    case GetTask(task) =>
      sender() ! tasks.find(_.name == task)

    case CreateTask(task) =>
      tasks += requestToTask(task)
      sender() ! ActionPerformed(s"Task ${task.name} created.")

    case CreateJob(job) =>
      jobs += requestToJob(job)
      sender() ! ActionPerformed(s"Job ${job.name} created.")

    case DeleteJob(job) =>
      jobs = jobs.filterNot(_.name == job)
      sender() ! ActionPerformed(s"Job $job deleted")

    case StartJob(job) =>
      jobs.find(_.name == job).fold(
        sender ! ActionFailed(s"No job named $job")
      ) { j =>
          jobRunner ! RunJob(j)
          sender() ! ActionPerformed(s"Job $job started")
        }

    case UpdateLastRunAt(job, time) =>
      val newJob = job.copy(lastRunAt = Some(time))
      jobs = jobs - job + newJob

  }

}

