package com.orchestrator

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ DateTime, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.util.Timeout
import akka.pattern.ask
import com.orchestrator.actors.JobRegistryActor._
import com.orchestrator.actors.JobRunnerActor._
import com.orchestrator.actors.{ Job, Jobs, Task, Tasks }
import spray.json.{ DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, JsonFormat, deserializationError }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

case class TaskRequest(name: String, command: String)

case class JobRequest(name: String, tasks: List[String], frequency: JobRequestFrequency)

case class JobRequestFrequency(seconds: Option[Int], minutes: Option[Int], hours: Option[Int], days: Option[Int], once: Option[Boolean]) {
  def validate: Boolean = {
    val defined = List(
      this.seconds.isDefined,
      this.minutes.isDefined,
      this.hours.isDefined,
      this.days.isDefined,
      this.once.isDefined
    ).count(_ == true)
    if (defined == 1) true
    else false
  }

  def toDuration: Duration = {
    this.seconds.map(s => s.seconds).getOrElse(
      this.minutes.map(m => m.minutes).getOrElse(
        this.hours.map(h => h.hours).getOrElse(
          this.days.map(d => d.days).getOrElse(
            Duration.Inf
          )
        )
      )
    )
  }
}

trait JobRequestJsonSupport extends SprayJsonSupport {
  import DefaultJsonProtocol._

  // For converting Duration -> Json
  implicit object DurationJsonFormat extends JsonFormat[Duration] {
    def write(x: Duration) =
      JsObject("seconds" -> JsNumber(x.toSeconds))

    def read(value: JsValue): Duration = value match {
      case x: JsObject =>
        val fields = x.fields
        if (fields.keys.size != 1) deserializationError("Expected only one field in JsObject, got " + fields.keys.size)
        else {
          val key = fields.keys.head
          FiniteDuration(fields(key).convertTo[Long], key)
        }
      case x => deserializationError("Expected Int as JsNumber, but got " + x)
    }
  }

  // For converting DateTime -> Json
  implicit object DateJsonFormat extends JsonFormat[DateTime] {
    override def write(obj: DateTime) = JsString(obj.toIsoLikeDateTimeString())

    override def read(json: JsValue): DateTime = json match {
      case JsString(s) => DateTime.fromIsoDateTimeString(s).getOrElse(deserializationError("Could't parse string into DateTime" + s))
      case x => deserializationError("Expected Int as JsString, but got " + x)
    }
  }

  implicit val taskRequestJson = jsonFormat2(TaskRequest)
  implicit val jobRequestFrequencyJson = jsonFormat5(JobRequestFrequency)
  implicit val jobRequestJson = jsonFormat3(JobRequest)

  implicit val taskJson = jsonFormat2(Task)
  implicit val taskListJson = jsonFormat1(Tasks)
  implicit val jobJson = jsonFormat4(Job)
  implicit val jobListJson = jsonFormat1(Jobs)

  implicit val actionPerformedJson = jsonFormat1(ActionPerformed)
  implicit val actionFailedJson = jsonFormat1(ActionFailed)
}

trait JobRoutes extends JobRequestJsonSupport {

  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext

  def jobRunnerActor: ActorRef
  def jobRegistryActor: ActorRef

  lazy val log = Logging(system, classOf[JobRoutes])

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val jobRoutes: Route =
    pathPrefix("jobs") {
      concat(
        pathEnd {
          concat(
            get {
              val jobs: Future[Jobs] = (jobRegistryActor ? GetJobs).mapTo[Jobs]
              complete(jobs)
            },
            post {
              entity(as[JobRequest]) { jobRequest =>
                log.info(s"$jobRequest")
                if (jobRequest.frequency.validate) {
                  val jobCreated = (jobRegistryActor ? CreateJob(jobRequest)).mapTo[ActionPerformed]
                  onSuccess(jobCreated) { performed =>
                    log.info("Created job [{}]: {}", jobRequest.name, performed.description)
                    complete((StatusCodes.Created, performed))
                  }
                } else complete((StatusCodes.BadRequest, ActionFailed("Unusable job frequency")))
              }
            }
          )
        },
        path("run") {
          concat(
            path(Segment) { name =>
              concat(
                post {
                  val maybeJob: Future[Option[Job]] =
                    (jobRegistryActor ? GetJob(name)).mapTo[Option[Job]]
                  maybeJob.foreach(_.foreach(j => jobRunnerActor ! RunJob(j)))
                  rejectEmptyResponse {
                    complete(maybeJob)
                  }
                }
              )
            }
          )
        },
        path(Segment) { name =>
          concat(
            get {
              val maybeJob: Future[Option[Job]] =
                (jobRegistryActor ? GetJob(name)).mapTo[Option[Job]]
              rejectEmptyResponse {
                complete(maybeJob)
              }
            },
            delete {
              val jobDeleted: Future[ActionPerformed] =
                (jobRegistryActor ? DeleteJob(name)).mapTo[ActionPerformed]
              onSuccess(jobDeleted) { performed =>
                log.info("Deleted job [{}]: {}", name, performed.description)
                complete((StatusCodes.OK, performed))
              }
            }
          )

        }
      )
    }

  lazy val taskRoutes: Route =
    pathPrefix("tasks") {
      concat(
        pathEnd {
          concat(
            get {
              val tasks: Future[Tasks] = (jobRegistryActor ? GetTasks).mapTo[Tasks]
              complete(tasks)
            },
            post {
              entity(as[TaskRequest]) { taskRequest =>
                val jobCreated = (jobRegistryActor ? CreateTask(taskRequest)).mapTo[ActionPerformed]
                onSuccess(jobCreated) { performed =>
                  log.info("Created task [{}]: {}", taskRequest.name, performed.description)
                  complete((StatusCodes.Created, performed))
                }
              }
            }
          )
        },
        path("run" / Segment) { name =>
          concat(
            post {
              log.info(name)
              val maybeTask: Future[Option[Task]] =
                (jobRegistryActor ? GetTask(name)).mapTo[Option[Task]]
              maybeTask.foreach(_.foreach { j =>
                jobRunnerActor ! RunTask(j)
              })
              rejectEmptyResponse {
                complete(maybeTask)
              }
            }
          )
        },
        path(Segment) { name =>
          concat(
            get {
              val maybeTask: Future[Option[Task]] =
                (jobRegistryActor ? GetTask(name)).mapTo[Option[Task]]
              rejectEmptyResponse {
                complete(maybeTask)
              }
            }
          )

        }
      )
    }

  lazy val allRoutes: Route = concat(jobRoutes, taskRoutes)

}
