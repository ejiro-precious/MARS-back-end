package com.utamars.api

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.std.all._
import com.github.nscala_time.time.Imports._
import com.utamars.ExeCtx
import com.utamars.dataaccess._
import com.utamars.util.{EMailer, UtaDate}

case class TimeSheetGenApi(implicit ec: ExeCtx, sm: SessMgr, rts: RTS) extends Api {

  override val defaultAuthzRoles = Seq(Role.Admin, Role.Instructor, Role.Assistant)

  private val monthYear =  parameter('month.as[Int], 'year.as[Int])

  override val route = {
    (get & pathPrefix("time-sheet") & authnAndAuthz(Role.Assistant)) { acc =>
      (path("first-half-month") & monthYear) { (month, year) =>
        asstMakeAndSendTimeSheet(acc.netId, month, year, isFirst = true)
      } ~
      (path("second-half-month") & monthYear) { (month, year) =>
        asstMakeAndSendTimeSheet(acc.netId, month, year, isFirst = false)
      }
    } ~
    (get & pathPrefix("time-sheet") & authnAndAuthz(Role.Admin, Role.Instructor)) { acc =>
      (path(Segment / "first-half-month") & monthYear) { (netId, month, year) =>
        InstMakeAndSendTimeSheet(acc, netId, month, year, isFirst = true)
      } ~
      (path(Segment / "second-half-month") & monthYear) { (netId, month, year) =>
        InstMakeAndSendTimeSheet(acc, netId, month, year, isFirst = false)
      }
    } ~
    (get & path("pay-period-info") & parameter('timestamp.as[Long].?) & authnAndAuthz()) { (ts, acc) => // todo: doc
      val today = ts.map(new LocalDate(_)).getOrElse(new LocalDate())
      val (start, end) = today.halfMonth
      complete {
        Map(
          "start" -> start.toStartOfDayTimestamp.getMillis,
          "end" -> end.toStartOfDayTimestamp.getMillis,
          "due" -> UtaDate.dueDate(end).toStartOfDayTimestamp.getMillis,
          "pay" -> UtaDate.payDate(end).toStartOfDayTimestamp.getMillis
        ).jsonCompat
      }
    }
  }

  private def InstMakeAndSendTimeSheet(acc: Account, netId: String, month: Int, year: Int, isFirst: Boolean): Route = {
    if ((1 to 12).contains(month) && (year > 0)) {
      val result = for {
        inst      <- Instructor.findByNetId(acc.netId)
        asst      <- Assistant.findByNetId(netId)
        payPeriod =  new LocalDate(year, month, if (isFirst) 1 else 28).halfMonth
        timeSheet <- TimeSheet.fromDateRange(payPeriod, asst)
        _         = EMailer.mailTo(inst.email, subject = timeSheet.nameWithoutExtension.replace("_", " "), timeSheet)
      } yield ()

      complete(result.reply(_ => OK))
    } else {
      complete(HttpResponse(BadRequest, entity = s"Not a valid date: month=$month year=$year"))
    }
  }

  private def asstMakeAndSendTimeSheet(netId: String, month: Int, year: Int, isFirst: Boolean): Route = {
    if ((1 to 12).contains(month) && (year > 0)) {
      val result = for {
        asst      <- Assistant.findByNetId(netId)
        payPeriod =  new LocalDate(year, month, if (isFirst) 1 else 28).halfMonth
        timeSheet <- TimeSheet.fromDateRange(payPeriod, asst)
        _         = EMailer.mailTo(asst.email, subject = timeSheet.nameWithoutExtension.replace("_", " "), timeSheet)
      } yield ()

      complete(result.reply(_ => OK))
    } else {
      complete(HttpResponse(BadRequest, entity = s"Not a valid date: month=$month year=$year"))
    }
  }

}
