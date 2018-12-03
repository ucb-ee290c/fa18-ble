# Packet Assembler
 A packet assembler uses a finite state machine to collect data and form the packet for transmission. In **assembler.scala**, the BLE baseband was updated to accommodate to new Chisel standard (wrap PA Block as lazyModule in PAChain). The biggest improvement of design is that: in the former implementation, each packet("PABundle") includes *trigger* (1 bit, denotes the beginning of a BLE packet), *data* (8 bits, the contents of BLE packets) *crc_seed* (24 bits), *white_seed* (7 bits), and *done* (1 bit, denotes the end of a BLE packet). Thus each time when transmitting, the effective payload takes up only 8 bits out of 41 and most parts are repetitive for a single BLE packet. We improved the Bundle structure to include only *trigger* and *data*. The transmission is achieved with a FSM which holds 6 stages: idle, preamble, access address, PDU_header, PDU_payload, CRC. Several modifications were done for proper state transitions.
 
 
 The Matlab golden model is given below to explain how `pdu` is constructed.
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

## Test

A simple scala test is provided for the Packet Assembler module. To perform the unit test, type `sbt` in the root directory. After that, type `testOnly PacketAssembler.test.PacketAssemblerTester` in the sbt terminal.
