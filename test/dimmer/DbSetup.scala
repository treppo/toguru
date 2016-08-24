package dimmer

import java.io.IOException
import java.sql.{SQLException, Connection, DriverManager}
import scala.concurrent.duration._
import com.typesafe.config.Config

import scala.concurrent.{Await, Future}
import scala.sys.process.{Process, ProcessIO}
import scala.util.Random


trait DbSetup {

  def log(message: String): Unit
  def config: Config

  final def waitForPostgres(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val future = Future {
      while(true) {
        var maybeConn: Option[Connection] = None
        try {
          val url = config.getString("slick.db.url")
          val conn = DriverManager.getConnection(url)
          maybeConn = Some(conn)

          val query = config.getString("slick.db.connectionTestQuery")
          val statement = conn.createStatement()
          val rs = statement.executeQuery(query)
          rs.next()

          log("Postgres ready")
          break
        } catch {
          case _ : IOException =>
          case _ : SQLException =>
        } finally {
          maybeConn.foreach(c => c.close())
        }
        Thread.sleep(500)
      }
    }
    Await.result(future, 20.seconds)
  }



  var maybePostgresName:Option[String] = None

  def startPostgres() = {
    import scala.sys.process._

    def run(prefix: String, command: String): Int = {
      val logger = ProcessLogger(line => log(s"$prefix: $line"), line => log(s"$prefix: $line"))
      Process(command).run(logger).exitValue()
    }


    val postgresDockerImage = "postgres:local"
    val suffix = {
      val n = Random.nextLong
      (if (n == Long.MinValue) 0 else Math.abs(n)).toString.take(8)
    }

    val postgresName = s"postgres-$suffix"
    maybePostgresName = Some(postgresName)

    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      val buildExitValue = run("Build", s"docker build -t $postgresDockerImage -f Dockerfile.Postgres .")

      if(buildExitValue != 0)
        throw new RuntimeException("Docker build failed")

      val imageFilter = s"ancestor=$postgresDockerImage"

      val runningContainers = s"docker ps --filter $imageFilter --filter status=running -q".!!.trim.split("\n").filter(_.nonEmpty)
      if (runningContainers.nonEmpty) {
        log(s"Stopping running Postgres container(s) ${runningContainers.mkString(", ")}...")
        Process(s"docker stop ${runningContainers.mkString(" ")}").!!
      }

      val exitValue = run("Postgres", s"docker run --name $postgresName -d -p 5432:5432 $postgresDockerImage")

      if(exitValue != 0)
        throw new RuntimeException("Starting docker container failed")

    }
  }

  def stopPostgres() = {
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global
    for (postgresName <- maybePostgresName) {
      val discardOutput = new ProcessIO(_.close, _.close, _.close)
      val kill = Process(s"docker kill $postgresName").run(discardOutput)
      Await.result(Future(kill.exitValue()), 10.seconds)
      val rm = Process(s"docker rm $postgresName").run(discardOutput)
      Await.result(Future(rm.exitValue()), 10.seconds)
    }
    maybePostgresName = None
  }


}
