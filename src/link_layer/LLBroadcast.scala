package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer

case class Msg(initiatorId: Byte, payload: String)
case class FatherMsg(fatherId: NodeId)
case class NeighbourMsg(neighbours: List[NodeId])
case class StartMsg(msg: String)

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
        println(s"Initiator decides")
        rec = 0
      }
    }
    case _ => println("ERROR I")
  }

}

class Follower(val comInterface: Broadcaster, initiatorId: Byte) extends Actor {
  var father: Option[NodeId] = None

  var neighbors: Option[List[NodeId]] = None
  var rec = 0
  def receive = {
    case FatherMsg(fatherId) =>
      father = Some(fatherId)
    case NeighbourMsg(neighboursList) =>
      neighbors = Some(neighboursList)
    case str: String => {
      rec = rec + 1

      if (rec == 1) {
        println(s"Actor gets informed by " + sender.path)
        for (q <- neighbors.get) {
          comInterface.sendUserMsg(q, str, initiatorId)
        }
      } else if (rec == neighbors.get.length) {
        comInterface.sendUserMsg(father.get, str, initiatorId)
        rec = 0
      }
    }
  }
}

abstract class Broadcaster(implicit val system: ActorSystem) extends ProtocolHandler[Msg](1, BroadcastCodec) {

  val remoteIds: List[NodeId]
  val localId: Byte

  var initiator: ActorRef = null;
  var followers: Map[Byte, ActorRef] = Map()

  override def acceptMsgFrom(msg: Msg, from: NodeId): Unit = {
    msg match {
      case Msg(id, str) =>
        println(s"$id Client received $str");
        if (id == localId) {
          // is initiator
          initiator ! str
        } else {
          var value: Option[ActorRef] = followers.get(id)
          var follower: ActorRef = null
          if (value == None) {
            follower = system.actorOf(Props(classOf[Follower], this, id), name = "Follower_" + id)
            followers + (id -> follower)

            follower ! FatherMsg(from)
            follower ! NeighbourMsg(remoteIds.filterNot(e => e == from))

          } else {
            follower = value.get
          }
          follower ! str
        }
      case x: Any => println("Client received msg with wrong format " + msg)
    }
  }

  def sendUserMsg(id: NodeId, str: String, initiatorId: Byte) {
    println("DEBUG client should send " + str)
    sendMsg(id, Msg(initiatorId, str))
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
  }

  linkLayer.registerProtocolHandler(BroadcastInst)

  Thread.sleep(1000)

  for (i <- 1 to 10) {
    //BroadcastInst.sendUserMsg("Hallo Nr " + i)
  }

}