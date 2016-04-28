package org.elu.akka

import akka.actor.{Props, ActorPath, ActorRef, ActorSystem}
import scala.concurrent.duration._
import akka.pattern.{ask, pipe}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class TestWorker(masterLocation: ActorPath) extends Worker(masterLocation) {
  // We'll use the current dispatcher for the execution context.
  // You can use whatever you want.
  implicit val ec = context.dispatcher

  def doWork(workSender: ActorRef, msg: Any): Unit = {
    Future {
      workSender ! msg
      WorkComplete("done")
    } pipeTo self
  }
}

class BadTestWorker(masterLocation: ActorPath) extends Worker(masterLocation) {
  // We'll use the current dispatcher for the execution context.
  // You can use whatever you want.
  implicit val ec = context.dispatcher

  def doWork(workSender: ActorRef, msg: Any): Unit = context.stop(self)
}

class WorkerSpec extends TestKit(ActorSystem("WorkerSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with MustMatchers {

  implicit val ec = system.dispatcher

  implicit val askTimeout = Timeout(1 second)
  override def afterAll() {
    system.terminate()
  }

  def worker(name: String) = system.actorOf(Props(
    new TestWorker(ActorPath.fromString(
      "akka://%s/user/%s".format(system.name, name)))))

  def badWorker(name: String) = system.actorOf(Props(
    new BadTestWorker(ActorPath.fromString(
      "akka://%s/user/%s".format(system.name, name)))))

  "Worker" should {
    "work" in {
      // Spin up the master
      val m = system.actorOf(Props[Master], "master")
      // Create three workers
      val w1 = worker("master")
      val w2 = worker("master")
      val w3 = worker("master")
      // Send some work to the master
      m ! "Hithere"
      m ! "Guys"
      m ! "So"
      m ! "What's"
      m ! "Up?"
      // We should get it all back
      expectMsgAllOf("Hithere", "Guys", "So", "What's", "Up?")
    }

    "still work if one dies" in { //{2
    // Spin up the master
    val m = system.actorOf(Props[Master], "master2")
      // Create three workers
      val w1 = worker("master2")
      val w2 = badWorker("master2")
      // Send some work to the master
      m ! "Hithere"
      m ! "Guys"
      m ! "So"
      m ! "What's"
      m ! "Up?"
      // We should get it all back
      expectMsgAllOf("Hithere", "Guys", "So", "What's", "Up?")
    }

    "work with Futures" in {
    // Spin up the master
    val m = system.actorOf(Props[Master], "master3")
      // Create three workers
      val w1 = worker("master3")
      val w2 = worker("master3")
      val w3 = worker("master3")
      val fs = Future.sequence(List("Hithere", "Guys", "So", "What's", "Up?").map { s => m ? s })
      Await.result(fs, 1 second) must be (List("Hithere", "Guys", "So", "What's", "Up?"))
      // We should get it all back
    }
  }
}