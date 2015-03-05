package app_layer

import scala.collection.mutable.Queue
import scala.collection.mutable.Set
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import link_layer.Broadcaster
import link_layer.LinkLayer.NodeId

case class Msg(initiatorId: Byte, payload: String)
case class FatherMsg(fatherId: NodeId)
case class NeighbourMsg(neighbours: List[NodeId], msg: String)
case class StartMsg(msg: String)
case class Activator(phil: Philosoph)
case object EmptyStomach
case object FullStomach
case object AccessGranted

class MutexHandler(comInterface: Broadcaster, philId: Int, mutName1: String, mutName2: String) extends Actor {
  import context._

  var mutTimeStamp = 0
  var logicalClock = 0

  var mutLock1 = false
  val replyQueue: Queue[String] = Queue()

  var msgSet: Set[String] = Set()

  var philosoph: Philosoph = null;

  def receive = {
    case Activator(phil) => {
      philosoph = phil
      println("State change: init to released")
      become(released)
    }
    case _ => println("Error Startzustand")
  }

  def released: Receive = {
    case msg: String =>
      if (isNewMessage(msg) && isMyMutex(msg)) {
        println("Mutex sending reply")
        updateClock(msg.split('|')(2))
        comInterface.startBroadcast("REPLY|" + msg.split('|')(1) + "|" + logicalClock + "|" + philId)
        updateClock("0")
      }

    case EmptyStomach => {
      updateClock("0")
      mutTimeStamp = logicalClock
      comInterface.startBroadcast("REQUEST|" + mutName1 + "|" + mutTimeStamp + "|" + philId)
      updateClock("0")
      println("State change: released to requested")
      become(requested)
    }
    case _ => println("Error released")
  }

  def requested: Receive = {
    case str: String => {
      var list = str.split('|')
      updateClock(list(2))
      list(0) match {
        case "REPLY" => {
          if (!mutLock1) {
            mutLock1 = true
            comInterface.startBroadcast("REQUEST|" + mutName2 + "|" + mutTimeStamp + "|" + philId)
            updateClock("0")
          } else {
            philosoph.canEat = true
            println("State change: requested to held")
            become(held)
          }
        }
        case "REQUEST" => {
          if (isMyMutex(str)) {
            var otherTimeStamp = Integer.parseInt(list(2))
            var reply = "REPLY|" + list(1) + "|" + logicalClock + "|" + philId
            if (otherTimeStamp < mutTimeStamp) {
              comInterface.startBroadcast(reply)
              updateClock("0")

            } else {
              var otherPhilID = Integer.parseInt(list(3))
              if ((otherTimeStamp == mutTimeStamp) && (otherPhilID < philId)) {
                comInterface.startBroadcast(reply)
                updateClock("0")
              } else replyQueue.enqueue(reply)
            }
          }
        }
      }
    }
    case _ => println("Error requested")
  }

  def held: Receive = {
    case FullStomach => {
      mutLock1 = false
      updateClock("0")
      while (!replyQueue.isEmpty) {
        comInterface.startBroadcast(replyQueue.dequeue);
        updateClock("0")
      }
      println("State change: held to released")
      become(released)
    }
    case str: String => {
      var list = str.split('|')
      updateClock(list(2))
      if (isMyMutex(str)) {
        replyQueue.enqueue("REPLY|" + list(1) + "|" + logicalClock + "|" + philId)
      }
    }
    case _ => println("Error held")
  }

  def isNewMessage(msg: String): Boolean = {
    if (!msgSet.contains(msg)) {
      println("Is new message")
      msgSet.add(msg)
      return true
    }
    println("Is old message")
    return false
  }

  def isMyMutex(str: String): Boolean = {
    var list = str.split('|')
    if (list(1) == mutName1 || list(1) == mutName2) {
      println("Is my mutex")
      return true
    } else {
      println("Is not my mutex")
      return false
    }

  }

  def updateClock(ts: String) {
    logicalClock = Math.max(logicalClock, ts.toInt) + 1
  }
}

class Philosoph(val mutexHandler: ActorRef) extends Thread {

  val random = new Random();
  @volatile var canEat = false

  override def run() {
    mutexHandler ! Activator(this)
    Thread.sleep(10000)
    while (true) {
      think();
      eat();
    }
  }

  private def think() {
    var theory = random.nextDouble() * 100;
    while (theory > 0.000001) {
      theory = theory - 0.000001 * theory;
    }
    theory = random.nextDouble() * 100;
  }

  private def eat() {
    mutexHandler ! EmptyStomach

    while (!canEat) {
      // nothing to do here
    }

    println(s"Phil starts eating")
    Thread.sleep(1000);
    println(s"Phil stops eating")
    canEat = false;
    mutexHandler ! FullStomach
  }

}