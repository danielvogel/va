package physical_layer

import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import akka.io.{IO, Udp}
import akka.util.{ByteString, ByteIterator}
import java.net.InetSocketAddress

class UdpProxy(localAdr: InetSocketAddress, remoteAdr: InetSocketAddress, udpDevice: UDPNetworkDevice) extends Actor {
    import context.system
    
    
    IO(Udp) ! Udp.Bind(self, localAdr)
    
     
    def receive = {
      case Udp.CommandFailed(cmd) => println("Could not bind to " + remoteAdr)
      case Udp.Bound(local) =>
        val partner = sender() 
        context become {
             case Udp.Received(data, remote) =>
               val byteIter: ByteIterator = data.iterator
               val bytes: Array[Byte] =  data.toArray[Byte]
               udpDevice.callBack(bytes)
             case msg: Array[Byte] =>
               partner ! Udp.Send(ByteString(msg), remoteAdr) 
         }
    }    
}


class UDPNetworkDevice(myPort: Int, remotePort: Int)(implicit system: ActorSystem) extends NetworkDevice {
  val proxy = system.actorOf(
      Props(classOf[UdpProxy], 
          new InetSocketAddress("127.0.0.1", myPort),
          new InetSocketAddress("127.0.0.1", remotePort),
          this), "udpInterface"+myPort)
  
  override def sendWire(msg: Array[Byte]) : Unit = {
    proxy ! msg
  }
}