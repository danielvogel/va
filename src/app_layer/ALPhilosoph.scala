package app_layer

/**
 * Created by mario on 04.03.15.
 */


import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer
import scala.util.Random

case class Msg(initiatorId: Byte, payload: String)
case class FatherMsg(fatherId: NodeId)
case class NeighbourMsg(neighbours: List[NodeId], msg: String)
case class StartMsg(msg: String)
case class Activator(phil : Philosoph)
case object EmptyStomach
case object FullStomach
case object AccessGranted

class Initiator(val comInterface: Broadcaster) extends Actor {
  var rec = 0

  def receive = {
    case StartMsg(msg) => {
      for (n <- comInterface.remoteIds) {
        comInterface.sendUserMsg(n, msg, comInterface.localId)
      }
    }
    case str: String => {
      rec = rec + 1
      if (rec == comInterface.remoteIds.length) {
        println("Initiator decides")
        comInterface.mutexHandler ! AccessGranted
        rec = 0
      }
    }
    case _ => println("ERROR I")
  }

}




class Follower(val comInterface: Broadcaster, initiatorId: Byte) extends Actor {
  var father: Option[NodeId] = None
  var access = false
  var neighbors: Option[List[NodeId]] = None
  var rec = 0
  var lastMsg : String = ""
  println(s"Creating new follower '$this'")

  def receive = {
    case FatherMsg(fatherId) =>
      father = Some(fatherId)
    case NeighbourMsg(neighboursList, msg) => {
      comInterface.mutexHandler ! msg
      neighbors = Some(neighboursList)
      for (q <- neighbors.get) {
        comInterface.sendUserMsg(q, msg, initiatorId)
      }
      rec = rec + 1
      lastMsg = msg
      // Not Used due to the new access var; Broadcast answer must be permitted by MutexHandler
      /*
      if (rec == neighbors.get.length + 1) {
        comInterface.sendUserMsg(father.get, msg, initiatorId)
        rec = 0
        println(s"Follower finished local broadcast from '$initiatorId')")
      }
      */
    }
    case AccessGranted =>{
      access = true
      if (rec == neighbors.get.length + 1 && access) {
        comInterface.sendUserMsg(father.get, lastMsg, initiatorId)
        rec = 0
        println(s"Follower finished local broadcast from '$initiatorId')")
      }
    }
    case str: String => {
      rec = rec + 1
      if (rec == neighbors.get.length + 1 && access) {
        comInterface.sendUserMsg(father.get, str, initiatorId)
        rec = 0
        println(s"Follower finished local broadcast from '$initiatorId')")
      }
    }
  }
}





class MutexHandler(comInterface: Broadcaster, philId : Int,mutName1 :String, mutName2 :String) extends Actor {
  import context._
  var mutTimeStamp = 0
  var mutLock1 = false
  val queue = new scala.collection.mutable.Queue[ActorRef]
  //var mutLock2 = false  not needed
  var philosoph : Philosoph = null

  def receive = {
    case Activator(phil) => {
      philosoph = phil
      become(released)
    }
    case _ => println("ERROR I")
  }

  def released: Receive = {
    case msg : String => sender ! AccessGranted
    case EmptyStomach => {
      comInterface.logicalClock +=1
      mutTimeStamp = comInterface.logicalClock
      comInterface.startBroadcast(mutName1+"|"+mutTimeStamp+"|"+philId)
      become(requested)
    }
    case _ => println("ERROR I")
  }

  def requested: Receive = {
    case AccessGranted => {
      if(!mutLock1){
        mutLock1 = true
        comInterface.startBroadcast(mutName2+"|"+mutTimeStamp+"|"+philId)
      }else{
        philosoph.notify()
        become(held)
      }
    }
    case str: String => {
      var list = str.split("|")
      if(list(0) == mutName1 || list(0) == mutName2){
        var otherTimeStamp = Integer.parseInt(list(1))
        if(otherTimeStamp < mutTimeStamp) sender ! AccessGranted
        else{
          var otherPhilID = Integer.parseInt(list(2))
          if((otherTimeStamp == mutTimeStamp) && (otherPhilID < philId)) sender ! AccessGranted
          else queue.enqueue(sender)
        }
      }else{
        sender ! AccessGranted
      }
    }
    case _ => println("ERROR I")
  }

  def held: Receive = {
    case FullStomach => {
      mutLock1 = false
      while(!queue.isEmpty) queue.dequeue() ! AccessGranted
      become(released)
    }
    case str: String => {
      var list = str.split("|")
      if(list(0) == mutName1 || list(0) == mutName2){
        queue.enqueue(sender)
      }
      else{
        sender ! AccessGranted
      }
    }
    case _ => println("ERROR I")
  }


}

class Philosoph(val mutexHandler : ActorRef) extends Thread{

  val random = new Random();

  override def run () {
    mutexHandler ! Activator(this)
    Thread.sleep(3000)
    while (true) {
      think();
      mutexHandler ! EmptyStomach
      eat();
      mutexHandler ! FullStomach
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
    wait()
    println(s"Phil $id starts eating")
    Thread.sleep(1000);
    println(s"Phil $id stops eating")
  }

}