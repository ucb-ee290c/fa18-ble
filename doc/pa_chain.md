# PA_Chain
 PA_Chain connects a packet assembler to Rocketchip. By using C code, BLE packet is sent bundle by bundle to an asynchronous FIFO. Then, the FIFO is connected to the packet assembler, and the other side of the packet assembler is connected to another asynchronous FIFO, which serves as a purpose of checking the result. 
 
 ## Input and Output Ports
Connection among stream nodes:
```
readQueue.streamNode := packet.streamNode := writeQueue.streamNode
```
* `writeQueue` is the FIFO at the input side of the packet assembler. `readQueue` is the FIFo at the output side of the packet assembler. 
