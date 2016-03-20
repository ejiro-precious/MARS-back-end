package com.utamars.api

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.StatusCodes._
import com.utamars.ApiSpec
import com.utamars.api.DAOs.AssistantDAO

class AssistantApiSpec extends ApiSpec {

  val api = AssistantApi()

  override def beforeAll(): Unit = {
    initDataBase()
    initDataBaseData()
  }

  "Instructor user" must {
    "be able to update assistant account info" in {
      instRequest(_ => Post(s"/assistant/${asstBob.netId}", FormData("rate" -> "16.64"))) ~> check {
        instRequest(_ => Get(s"/assistant/${asstBob.netId}")) ~> check {
          responseTo[AssistantDAO].rate should equal(16.64)
        }
      }
    }
  }


  "Assistant user" must {

    "be able to update their account info" in {
      asstRequest(_ => Post("/assistant", FormData("title" -> "new title"))) ~> check {
        asstRequest(_ => Get("/assistant")) ~> check {
          responseTo[AssistantDAO].title should equal("new title")
        }
      }
    }

    "not be able to change their account threshold value" in {
      asstRequest(_ => Post("/assistant", FormData("threshold" -> ".5"))) ~> check(status should equal(Forbidden))
    }

  }

}
