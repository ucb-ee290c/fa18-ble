# PA_Chain
 In **PAChain.scala**, we basically integrated a complete chain for PacketAssembler testing. PA_Chain connects a packet assembler to Rocketchip. By using C code, BLE packet is writen into an asynchronous FIFO bundle by bundle. The PA bundle is transmitting by AXI4StreamNode. We made diplomatic TL node for regmap and used WriteQueue/ReadQueue to access the testing bundle. Then, the FIFO is connected to the packet assembler, and the other side of the packet assembler is connected to another asynchronous FIFO, which serves as a purpose of checking the result. The diagram of PA chain is illustrated below:
 
 ![blockDiagram](image/pa_chain.jpg)
 
 ## Input and Output Ports
Connection among stream nodes:
```
readQueue.streamNode := packet.streamNode := writeQueue.streamNode
```
* `writeQueue` is the FIFO at the input side of the packet assembler. `readQueue` is the FIFO at the output side of the packet assembler. 
