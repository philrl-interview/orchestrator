package com.orchestrator

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.orchestrator.actors.JobRunnerActor._
import com.orchestrator.actors.{ JobRegistryActor, JobRunnerActor }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object OrchestratorApp extends App with JobRoutes {
  implicit val system: ActorSystem = ActorSystem("orchestratorServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val executionContext: ExecutionContext = system.dispatcher

  val jobRunnerActor: ActorRef = system.actorOf(JobRunnerActor.props, "job-runner")
  val jobRegistryActor: ActorRef = system.actorOf(JobRegistryActor.props, "job-registry")
  val reloadScheduler = system.scheduler.schedule(1.seconds, 1.seconds, jobRunnerActor, ScheduledRun)

  val routes: Route = allRoutes
  val serverBindingFuture: Future[ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)
  log.info(s"Server online at http://localhost:8080/")

  sys.addShutdownHook(
    serverBindingFuture
      .flatMap(_.unbind())
      .onComplete { done =>
        done.failed.map { ex => log.error(ex, "Failed unbinding") }
        reloadScheduler.cancel()
        system.terminate()
      }
  )
}
