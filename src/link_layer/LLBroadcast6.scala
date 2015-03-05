package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import physical_layer.{ Codec, UDPNetworkDevice, PhysicalLayer }
import link_layer.LinkLayer._
import scala.collection.mutable.ListBuffer
import app_layer.MutexHandler
import app_layer.Philosoph


object LLBroadcast6 extends App {
  val localNodeName = 6.toByte
  implicit val system = ActorSystem("UDPSystem")

  val devices: List[Triple[UDPNetworkDevice, NodeId, Symbol]] = List(
    Triple(new UDPNetworkDevice(4722, 4721), 5, 'A))

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
    lazy val mutexHandler: Option[ActorRef] = Some(mutHandler)
  }
  
  val mutHandler = system.actorOf(Props(classOf[MutexHandler], BroadcastInst,localNodeName.toInt,"mutexA","mutexB"), name = "MutexHandler" )

  linkLayer.registerProtocolHandler(BroadcastInst)
  println("Starting node " + localNodeName)

  Thread.sleep(10000)
  new Philosoph(mutHandler).start()
}