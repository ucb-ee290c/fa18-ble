# Packet Assembler
 A packet assembler collects data and forms the packet for transmission. The Matlab golden model is given below to explain how `pdu` is constructed.
 ```
 pdu = [pdu_header AdvA payload1 payload2_header payload2_data]
 ```
 Then, it utilizes CRC and whitening modules. Finally, it passes the whole packet to the next module. The Matlab golden model is given below to explain how `packet` is constructed.
 ```
 packet01 = [pre_preamble bpreamble baccess_address pdu_crc_whitened]
 ```
For the convenience of testing, the next module for `PA_chain` is an asynchronous FIFO, and the next module for `loop` is `PDA`.
 
 
 
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
Packet Assembler has two outputs. `data` is a 1-bit result. `done` is a 1-bit output that notifies the end of the packet.
