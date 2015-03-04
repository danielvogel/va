package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import physical_layer.{Codec, UDPNetworkDevice, PhysicalLayer}
import link_layer.LinkLayer._


abstract class EchoClient extends ProtocolHandler[EchoMsg](1, EchoCodec) {
  val remoteId : NodeId
  
  override def acceptMsgFrom(msg: EchoMsg, from: NodeId) : Unit = {
    msg match {
      case EchoReply(str) => println(s"Client received $str");
      case x: Any => println("Client received msg with wrong format " + msg) 
    }
  }
  
  def sendUserMsg(str: String) {
    println("DEBUG client should send " + str)
    sendMsg(remoteId, EchoRequest(str))
  }
  
}

object LLEchoClient extends App {
  val myPort = 4712
  val remotePort = 4711
  
  val localNodeName = 2
  val partnerNodeName = 1
  
  implicit val system = ActorSystem("UDPSystem")
  
  val udpDevice = new UDPNetworkDevice(myPort, remotePort)
  
  object physicalLayerHere extends PhysicalLayer
  physicalLayerHere.installNetworkDevice('B, udpDevice)
  
  object linkLayer extends LinkLayer(localNodeName) { 
    val physicalLayer = physicalLayerHere 
  }
  
  linkLayer.connectPhysical('B, partnerNodeName) 

  object EchoClientInst extends EchoClient {
    val remoteId : NodeId = partnerNodeName
    val ll : LinkLayer = linkLayer
  }
  
  linkLayer.registerProtocolHandler(EchoClientInst)
  
  Thread.sleep(1000)
  
  for (i <- 1 to 10) {
    EchoClientInst.sendUserMsg("Hallo Nr " + i)
  }

}