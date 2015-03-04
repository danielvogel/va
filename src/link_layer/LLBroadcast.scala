package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer

case class Msg(initiatorId: Byte, payload: String)

class Initiator extends Actor {
  def receive = {
    case _ =>
      println("ok")
  }
}

class Follower extends Actor {
  def receive = {
    case _ =>
      println("ok")
  }
}

abstract class Broadcaster extends ProtocolHandler[Msg](1, BroadcastCodec) {
  val remoteIds: ListBuffer[NodeId]
  val localId: Byte

  var initiator: Initiator = null
  var follower: List[Follower] = List()

  override def acceptMsgFrom(msg: Msg, from: NodeId): Unit = {
    msg match {
      case Msg(id, str) => println(s"$id Client received $str");
      case x: Any => println("Client received msg with wrong format " + msg)
    }
  }

  def sendUserMsg(id: NodeId, str: String) {
    println("DEBUG client should send " + str)
    sendMsg(id, Msg(localId, str))
  }

  def sendBroadcast(str: String) {
    println("DEBUG client starts broadcast " + str)
    for (n <- remoteIds)
      sendUserMsg(n, str)
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
    val remoteIds: ListBuffer[NodeId] = nodeNames
    val ll: LinkLayer = linkLayer
  }

  linkLayer.registerProtocolHandler(BroadcastInst)

  Thread.sleep(1000)

  for (i <- 1 to 10) {
    BroadcastInst.sendUserMsg("Hallo Nr " + i)
  }

}