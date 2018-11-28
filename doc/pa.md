# Packet Assembler
 A packet assmbler collects data from different sources. Then, it utilizes CRC and whitening modules. Finally, it passes the whole packet to the next module. For the convenience of testing, the next module for `PA_chain` is an asynchronous FIFO, adn the next module for `loop` is `PDA`.
 
 
 
 ## Input and Output Ports
 ```
 class PAInputBundle extends Bundle {
       val trigger = Output(Bool())
       val data = Output(UInt(8.W))
       
       override def cloneType: this.type = PAInputBundle().asInstanceOf[this.type]
}
 ```
Packet Assembler has two inputs. `trigger` notifies the beginning of the packet. `data` is an 8-bit sequence from BLE packet. 

```
class PAOutputBundle extends Bundle {
	val data = Output(UInt(1.W))
	val done = Output(Bool())

	override def cloneType: this.type = PAOutputBundle().asInstanceOf[this.type]
}
```
Packet Assembler has two outputs. `data` is an 1-bit result. `done` is an 1-bit output that notifies the end of the packet.
