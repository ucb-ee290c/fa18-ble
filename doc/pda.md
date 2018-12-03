# Packet DisAssembler
The packet disassembler is a finite state machine that receives data and sends it back to CPU. The 1-bit output of PA chain would be fed back into the packet disassembler for processing. Then, it has two tasks: utilize CRC and de-whitening modules; check AA and CRC. Finally, it passes the whole packet to the next module. For the convenience of testing, the next module for `PDA_chain` is also an asynchronous FIFO.
 
 
 
 ## Input and Output Ports
 ```
  class PDAInputBundle extends Bundle {
	    val switch = Output(Bool())
      val data = Output(UInt(1.W))//decouple(source): data, pop, empty

	override def cloneType: this.type = PDAInputBundle().asInstanceOf[this.type]
}
 ```
Packet DisAssembler has two inputs. `switch` denotes the beginning of the packet. `data` is the 1-bit output from PA chain. 

```
  class PDAOutputBundle extends Bundle {
    val data = Output(UInt(8.W))//decouple(sink): data, push, full
    val length = Output(UInt(8.W))
	  val length_valid = Output(Bool())
    val flag_aa = Output(Bool())
	  val flag_aa_valid = Output(Bool())
    val flag_crc = Output(Bool())
	  val flag_crc_valid = Output(Bool())
	  val done = Output(Bool())

	override def cloneType: this.type = PDAOutputBundle().asInstanceOf[this.type]
}
```
Packet DisAssembler has eight outputs. `data` is an 8-bit result. `done` is a 1-bit output that denotes the end of the packet. The rest outputs are needed for PDA processing but not part of the payload information.

 ## Test
 Due to time constraints, we did not build a Scala test for the Packet DisAssembler module. In return, a top level test is provided for PDA chain. Please refer to [here](https://github.com/ucberkeley-ee290c/fa18-ble/tree/master/doc/pda_chain.md).
