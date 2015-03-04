package physical_layer

trait Codec[T] {
  // encode msg as byte-array
  def encode(msg: T): Array[Byte]
  
  // decode msg from byte-array
  def decode(bytes: Array[Byte]): T
}
