package link_layer

import physical_layer.{PhysicalLayer, Codec, NetworkDriver}

import LinkLayer._
import physical_layer.Codec


// a protocol handler is installed at a link layer,
// it deals with messages that arrive at any device by
// dispatching them to the appropriate protocol handler
abstract class ProtocolHandler[MSG_T <: Any](val protId: ProtocolId, val codec: Codec[MSG_T]) {

  // Link layer instance to be used
  protected val ll: LinkLayer
  
  // used to transfer msgs; to be used by derived classes 
  protected final def sendMsg(dest: NodeId, msg: MSG_T) : Unit = {
    val bytes : Array[Byte] = codec.encode(msg)
    ll.sendTo(protId, dest, bytes)
  }
  
  // called when a msg for this protocol arrives; to be defined by derived classes
  def acceptMsgFrom(msg: MSG_T, from: NodeId) : Unit
}


object FrameCodec extends Codec[Frame]{
  def encode(msg: Frame): Array[Byte] =
    msg match {
      case Frame(protocolID: ProtocolId, payload: Array[Byte]) => 
        val payloadLen = payload.length
        val result = new Array[Byte](payloadLen+1)
        result(0) = protocolID.toByte
        payload.copyToArray(result, 1)
        result
    }
  def decode(ba: Array[Byte]): Frame = 
    Frame(ba(0).toInt, ba.slice(1, ba.length))
}

object LinkLayer {
  type NodeId = Int
  type DeviceId = Symbol
  type ProtocolId = Int
  
  // a serial line frame
  case class Frame(protocolID: ProtocolId, payload: Array[Byte])
}


abstract class LinkLayer(val localAddr: NodeId) {
  
  // the physical layer to be used
  protected val physicalLayer: PhysicalLayer
  
  // all installed protocol handlers, protocol handlers are identified by protocol ids 
  private var handlers : Map[ProtocolId, ProtocolHandler[Any]] = Map[ProtocolId, ProtocolHandler[Any]]()
  
  // maps devices to the node that may be reached via this device
  private var deviceToNode: Map[DeviceId, NodeId] = Map[DeviceId, NodeId]()
  // maps directly connected nodes to the device via which they may be reached
  private var nodeToDevice: Map[NodeId, DeviceId] = Map[NodeId, DeviceId]()
  
  def sendTo(protId: ProtocolId, dest: NodeId, payload: Array[Byte]) {
    physicalLayer.devices(nodeToDevice(dest)).driver.sendMsg(Frame(protId, payload))
  }  
  
  // connect to remote system
  // deviceID: the device to be used 
  // remoteAdr: the link layer address of the remote node
  def connectPhysical(deviceId: DeviceId, remoteAdr: NodeId) : Unit = {
    physicalLayer.installDriver(
          deviceId,
          new NetworkDriver[Frame](FrameCodec) {
              val device = physicalLayer.devices(deviceId)
              override def acceptMsg(msg: Frame) : Unit = 
                msg match {
                  case Frame(protocolId, payload) =>
                  // dispatch to handler for protocolId
                  handlers(protocolId).acceptMsgFrom(handlers(protocolId).codec.decode(payload), deviceToNode(deviceId))
                  case _ => throw new Exception("unexpected Msg!")   
              }
          })
    
      deviceToNode = deviceToNode + (deviceId -> remoteAdr)
      nodeToDevice = nodeToDevice + (remoteAdr -> deviceId)
  }
  
  
  def registerProtocolHandler[MSG_T <: Any](handler:  ProtocolHandler[MSG_T]) : Unit = {
    handlers = handlers + (handler.protId -> handler.asInstanceOf[ProtocolHandler[Any]])
  }        
 
  
}