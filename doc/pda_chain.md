# PDA_Chain
 PDA_Chain connects a packet disassembler back to Rocketchip. By using C code, the output of PA chain is sent bundle by bundle to an asynchronous FIFO. Then, the FIFO is connected to the packet disassembler. The other side of the packet disassembler is connected to another asynchronous FIFO, which serves as a purpose of checking the result. The process is quite similar to PA chain.
 
 ## Input and Output Ports
Connection among stream nodes:
```
readQueue.streamNode := packet.streamNode := writeQueue.streamNode
```
* `writeQueue` is the FIFO at the input side of the packet disassembler. `readQueue` is the FIFO at the output side of the packet disassembler. 
