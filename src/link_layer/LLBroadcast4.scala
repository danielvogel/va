package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer


object LLBroadcast4 extends App {
  val localNodeName = 4.toByte
  implicit val system = ActorSystem("UDPSystem")

  val devices: List[Triple[UDPNetworkDevice, NodeId, Symbol]] = List(
    Triple(new UDPNetworkDevice(4716, 4715), 2, 'A),
    Triple(new UDPNetworkDevice(4718, 4717), 3, 'B),
    Triple(new UDPNetworkDevice(4719, 4720), 5, 'C))

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
  println("Starting node " + localNodeName)

  Thread.sleep(1000)
}