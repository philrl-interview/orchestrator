package com.orchestrator

//#test-top
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.orchestrator.actors.{ JobRegistryActor, JobRunnerActor }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class JobRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
    with JobRoutes {

  override val jobRegistryActor: ActorRef =
    system.actorOf(JobRegistryActor.props, "job-registry")

  override val jobRunnerActor: ActorRef =
    system.actorOf(JobRunnerActor.props, "job-runner")

  val executionContext: ExecutionContext = executor

  lazy val routes = taskRoutes

  "TaskRoutes" should {
    "return no tasks if no present (GET /task)" in {

      val request = HttpRequest(uri = "/tasks")
      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"tasks":[]}""")
      }
    }

    "be able to add tasks (POST /tasks)" in {
      val task = TaskRequest("task1", "true")
      val taskEntity = Marshal(task).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Post("/tasks").withEntity(taskEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[String] should ===("""{"description":"Task task1 created."}""")
      }
    }

    "return tasks if present (GET /task)" in {
      val task = TaskRequest("task1", "true")
      val taskEntity = Marshal(task).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val post = Post("/tasks").withEntity(taskEntity)

      post ~> routes

      val request = HttpRequest(uri = "/tasks")
      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"tasks":[{"name":"task1","command":"true"}]}""")
      }
    }
  }

}
