# Loop
 In order to fully verified the functionality of `PA_Chain` and `PDA_Chain`, we connected the output of `PA_Chain` with the input of `PDA_Chain`. The input of the `PA_Chain` and the output of the `PDA_Chain` should be the same. For future work, the loop can be broken, and be connected to other modules.
 
 ## Input and Output Ports
 Connection among stream nodes:
 ```
 readQueue.streamNode := packet_pda.streamNode := transitionQueue.streamNode := packet_pa.streamNode := writeQueue.streamNode
 ```
 `writeQueue` is the FIFO at the input side of the packet assembler. `readQueue` is the FIFO at the input side of the packet disassembler. `transitionQueue` is the FIFO between the packet assembler and the packet disassembler. This FIFO first collects all outputs from the packet assembler, and after it finishes, it starts inputing the data to the packet disassembler. 
