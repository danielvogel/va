package link_layer

import physical_layer.Codec
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import physical_layer.{Codec, UDPNetworkDevice, PhysicalLayer}
import link_layer.LinkLayer._


abstract class EchoMsg
case class EchoRequest(msg: String) extends EchoMsg
case class EchoReply(msg: String) extends EchoMsg

object EchoCodec extends Codec[EchoMsg] {
  val ECHO_REQUEST: Byte = 0
  val ECHO_REPLY: Byte   = 1
  
  def encode(msg: EchoMsg): Array[Byte] = {
    msg match {
      case EchoRequest(str) => 
        val strBytes : Array[Byte]= str.getBytes
        val strBytesLen = strBytes.length
        val result = new Array[Byte](strBytesLen+1)
        result(0) = ECHO_REQUEST
        strBytes.copyToArray(result, 1)
        result
      case EchoReply(str) => 
        val strBytes = str.getBytes
        val strBytesLen = strBytes.length
        val result = new Array[Byte](strBytesLen+1)
        strBytes.copyToArray(result, 1)
        result(0) = ECHO_REPLY
        result
    } 
    
  }
  def decode(bytes: Array[Byte]): EchoMsg = {
    bytes(0) match {
      case ECHO_REQUEST =>
        EchoRequest(new String(bytes, 1, bytes.length-1))
      case ECHO_REPLY =>
        EchoReply(new String(bytes, 1, bytes.length-1))
    }
  }
}

abstract class EchoServer extends ProtocolHandler[EchoMsg](1, EchoCodec) {
  val remoteId : NodeId
  
  override def acceptMsgFrom(msg: EchoMsg, from: NodeId) : Unit = {
    println(s"Server received $msg from $from")
    msg match {
      case EchoRequest(str) => sendMsg(from, EchoReply("ECHO "+ str));
      case x: Any => println("Server received msg with wrong format " + msg) 
    }
  }
  
}


object LLEchoServer extends App {
  val myPort = 4711
  val remotePort = 4712
  
  val localNodeName = 1
  val partnerNodeName = 2
  
  implicit val system = ActorSystem("UDPSystem")
  
  val udpDevice = new UDPNetworkDevice(myPort, remotePort)
  
  object physicalLayerHere extends PhysicalLayer
  physicalLayerHere.installNetworkDevice('A, udpDevice)
  
  object linkLayer extends LinkLayer(localNodeName) { 
    val physicalLayer = physicalLayerHere 
  }
  
  linkLayer.connectPhysical('A, partnerNodeName) 

  object EchoServerInst extends EchoServer {
    val remoteId : NodeId = partnerNodeName
    val ll : LinkLayer = linkLayer
  }
  
  linkLayer.registerProtocolHandler(EchoServerInst)
  
}