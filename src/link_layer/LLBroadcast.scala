package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Queue

case class Msg(initiatorId: Byte, payload: String)
case class FatherMsg(fatherId: NodeId)
case class NeighbourMsg(neighbours: List[NodeId], msg: String)
case class StartMsg(msg: String)

class Initiator(val comInterface: Broadcaster) extends Actor {
  var rec = 0
  var broadcastQueue: Queue[String] = Queue()
  var isBroadcasting = false

  def receive = {
    case StartMsg(msg) => {
      if (isBroadcasting) {
        broadcastQueue.enqueue(msg)
        println("Currently executing. Storing message: 'msg' to queue")
      } else {
        sendBroadcast(msg)
      }
    }
    case str: String => {
      rec = rec + 1
      if (rec == comInterface.remoteIds.length) {
        println(s"Initiator finished. Current queue size: $broadcastQueue.size()")
        rec = 0
        isBroadcasting = false
        if (!broadcastQueue.isEmpty) {
          sendBroadcast(broadcastQueue.dequeue)
        }
      }
    }
    case _ => println("Error in Initiator")
  }

  def sendBroadcast(msg: String) {
    isBroadcasting = true
    for (n <- comInterface.remoteIds) {
      comInterface.sendUserMsg(n, msg, comInterface.localId)
    }
  }
}

class Follower(val comInterface: Broadcaster, initiatorId: Byte) extends Actor {
  var father: Option[NodeId] = None

  var neighbors: Option[List[NodeId]] = None
  var rec = 0

  println(s"Creating new follower '$this'")

  def receive = {
    case FatherMsg(fatherId) =>
      father = Some(fatherId)
    case NeighbourMsg(neighboursList, msg) => {
      neighbors = Some(neighboursList)
      for (q <- neighbors.get) {
        comInterface.sendUserMsg(q, msg, initiatorId)
      }
      rec = rec + 1

      if (rec == neighbors.get.length + 1) {
        comInterface.sendUserMsg(father.get, msg, initiatorId)
        rec = 0
        println(s"Follower finished local broadcast from '$initiatorId')")
      }
    }

    case str: String => {
      rec = rec + 1

      if (rec == neighbors.get.length + 1) {
        comInterface.sendUserMsg(father.get, str, initiatorId)
        rec = 0
        println(s"Follower finished local broadcast from '$initiatorId')")
      }
    }
  }
}

abstract class Broadcaster(implicit val system: ActorSystem) extends ProtocolHandler[Msg](1, BroadcastCodec) {

  val remoteIds: List[NodeId]
  val localId: Byte
  val mutexHandler: Option[ActorRef]

  var initiator: ActorRef = null;
  var followers: Map[Byte, ActorRef] = Map()

  override def acceptMsgFrom(msg: Msg, from: NodeId): Unit = {
    msg match {
      case Msg(id, str) =>
        if (id == localId) {
          // is initiator
          initiator ! str
          //println("Initiator received answer from '" + id + "'");
        } else {

          if (mutexHandler.nonEmpty)
            mutexHandler.get ! str

          var value: Option[ActorRef] = followers.get(id)
          println(s"Accepting msg '$str' with id '$id'")
          var follower: ActorRef = null
          if (value.isEmpty) {
          println(s"Could not find follower, creating new one.")
            follower = system.actorOf(Props(classOf[Follower], this, id), name = "Follower_" + id)
            followers += (id -> follower)

            follower ! FatherMsg(from)
            follower ! NeighbourMsg(remoteIds.filterNot(e => e == from), str)

          } else {
         	println(s"Could find follower '$value.get'")
            follower = value.get
            follower ! str
          }

        }
      case x: Any => println("Client received msg with wrong format " + msg)
    }
  }

  def sendUserMsg(id: NodeId, str: String, initiatorId: Byte) {
    println(s"Sending msg '$str' to '$id' (initiator: '$initiatorId')")
    sendMsg(id, Msg(initiatorId, str))
  }

  def startBroadcast(msg: String) {
    if (initiator == null) {
      initiator = system.actorOf(Props(classOf[Initiator], this), name = "Initiator")
    }
    initiator ! StartMsg(msg)
  }
}

object BroadcastCodec extends Codec[Msg] {

  def encode(msg: Msg): Array[Byte] = {
    msg match {
      case Msg(id, payload) =>
        val strBytesPayload: Array[Byte] = payload.getBytes
        val strBytesLen = 1 + strBytesPayload.length
        val result = new Array[Byte](strBytesLen)
        result(0) = id
        strBytesPayload.copyToArray(result, 1)
        result
    }
  }

  def decode(bytes: Array[Byte]): Msg = {
    Msg(bytes(0), new String(bytes, 1, bytes.length - 1))
  }
}

object LLBroadcast extends App {
  val localNodeName = 1.toByte
  implicit val system = ActorSystem("UDPSystem")

  val devices: List[Triple[UDPNetworkDevice, NodeId, Symbol]] = List(
    Triple(new UDPNetworkDevice(4711, 4712), 2, 'A))

  var nodeNames: ListBuffer[NodeId] = ListBuffer()

  object physicalLayerHere extends PhysicalLayer

  for (d <- devices)
    physicalLayerHere.installNetworkDevice(d._3, d._1)

  object linkLayer extends LinkLayer(localNodeName) {
    val physicalLayer = physicalLayerHere
  }

  for (d <- devices) {
    linkLayer.connectPhysical(d._3, d._2)
    nodeNames += d._2
  }

  object BroadcastInst extends Broadcaster {
    val localId: Byte = localNodeName
    val remoteIds: List[NodeId] = nodeNames.toList
    val ll: LinkLayer = linkLayer
    val mutexHandler = None
  }

  linkLayer.registerProtocolHandler(BroadcastInst)

  println("Starting node " + localNodeName)

  Thread.sleep(1000)

  //BroadcastInst.startBroadcast("Hallo")
}