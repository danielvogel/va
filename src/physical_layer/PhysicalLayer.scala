package physical_layer

trait NetworkDevice {
  var driver : NetworkDriver[Any] = null 
  
  // low level send
  def sendWire(msg: Array[Byte]) { }            
 
  // register callback routine for this device
  def register[MSG_T <: Any](driver: NetworkDriver[MSG_T]) : Unit = {
    this.driver = driver.asInstanceOf[NetworkDriver[Any]] 
  } 
  
  // callback routine for this device
  var callBack : Array[Byte] => Unit = { (bytes: Array[Byte]) => driver.acceptMsg( driver.codec.decode(bytes)) }
}

abstract class NetworkDriver[MSG_T <: Any](val codec: Codec[MSG_T]) {

  // low level routine for frame transfer; will be set when connected to a device
  //var sendWire: Array[Byte] => Unit = { bytes => println("no wire send installed "); }

  val device : NetworkDevice
  
  // used to transfer msgs; to be used by derived classes 
  final def sendMsg(msg: MSG_T) : Unit = {
    val bytes : Array[Byte] = codec.encode(msg)
    device.sendWire(bytes)
  }
  
  // called when a msg arrives; to be defined by derived classes
  def acceptMsg(msg: MSG_T) : Unit
}

class PhysicalLayer {
  
  // all devices; devices are identified by symbols
  var devices : Map[Symbol, NetworkDevice] = Map[Symbol, NetworkDevice]()
  
  // install a new device; a device is always wired
  def installNetworkDevice(deviceId: Symbol, networkDevice: NetworkDevice): Unit = {
    devices = devices + (deviceId -> networkDevice)
  }
  
  def getLinkIds : Set[Symbol] = devices.keySet
  
  // install a handler at a device; the handler will accept all incoming msgs 
  def installDriver[MSG_T](deviceId: Symbol, driver: NetworkDriver[MSG_T]): Unit = {
    
    val networkDevice = devices(deviceId)
    
    // install receive method of handler as callback of network device
    // the handler will get decoded bytes
    networkDevice.register(driver)
    
    // set low-level send routine
    //driver.sendWire = { bytes => networkDevice.sendWire(bytes) }
  } 
  
}